import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../models/sensorhub_models.dart';
import '../theme/app_theme.dart';

enum ChartMetric { temperature, humidity }

class MeasurementChart extends StatefulWidget {
  const MeasurementChart({
    required this.points,
    required this.metric,
    super.key,
  });

  final List<SeriesPoint> points;
  final ChartMetric metric;

  @override
  State<MeasurementChart> createState() => _MeasurementChartState();
}

class _MeasurementChartState extends State<MeasurementChart> {
  int? _selectedIndex;

  @override
  Widget build(BuildContext context) {
    final color = widget.metric == ChartMetric.temperature
        ? AppColors.temperature
        : AppColors.humidity;
    final title = widget.metric == ChartMetric.temperature
        ? 'Temperatura'
        : 'Umidade';
    final unit = widget.metric == ChartMetric.temperature ? '°C' : '%';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  widget.metric == ChartMetric.temperature
                      ? Icons.thermostat
                      : Icons.water_drop,
                  color: color,
                ),
                const SizedBox(width: 8),
                Text(title, style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
            const SizedBox(height: 14),
            SizedBox(
              height: 180,
              width: double.infinity,
              child: widget.points.isEmpty
                  ? const Center(child: Text('Sem dados no período'))
                  : LayoutBuilder(
                      builder: (context, constraints) {
                        return GestureDetector(
                          behavior: HitTestBehavior.opaque,
                          onTapDown: (details) => _selectNearestPoint(
                            details.localPosition,
                            constraints.biggest,
                          ),
                          onPanDown: (details) => _selectNearestPoint(
                            details.localPosition,
                            constraints.biggest,
                          ),
                          onPanUpdate: (details) => _selectNearestPoint(
                            details.localPosition,
                            constraints.biggest,
                          ),
                          onLongPressStart: (details) => _selectNearestPoint(
                            details.localPosition,
                            constraints.biggest,
                          ),
                          onLongPressMoveUpdate: (details) =>
                              _selectNearestPoint(
                                details.localPosition,
                                constraints.biggest,
                              ),
                          child: CustomPaint(
                            painter: _LineChartPainter(
                              points: widget.points,
                              metric: widget.metric,
                              color: color,
                              unit: unit,
                              selectedIndex: _selectedIndex,
                            ),
                            size: constraints.biggest,
                          ),
                        );
                      },
                    ),
            ),
          ],
        ),
      ),
    );
  }

  void _selectNearestPoint(Offset position, Size size) {
    if (widget.points.isEmpty) {
      return;
    }

    final chart = _chartRect(size);
    final x = position.dx.clamp(chart.left, chart.right);
    final minTime = widget.points.first.timestamp.millisecondsSinceEpoch;
    final maxTime = widget.points.last.timestamp.millisecondsSinceEpoch;
    final timeRange = math.max(maxTime - minTime, 1);
    final hasSinglePoint = widget.points.length == 1;

    var selectedIndex = 0;
    var smallestDistance = double.infinity;
    for (var i = 0; i < widget.points.length; i++) {
      final pointX = hasSinglePoint
          ? chart.center.dx
          : chart.left +
                ((widget.points[i].timestamp.millisecondsSinceEpoch - minTime) /
                        timeRange) *
                    chart.width;
      final distance = (pointX - x).abs();
      if (distance < smallestDistance) {
        smallestDistance = distance;
        selectedIndex = i;
      }
    }

    setState(() => _selectedIndex = selectedIndex);
  }
}

Rect _chartRect(Size size) {
  const left = 42.0;
  const right = 12.0;
  const top = 26.0;
  const bottom = 30.0;
  return Rect.fromLTRB(left, top, size.width - right, size.height - bottom);
}

class _LineChartPainter extends CustomPainter {
  const _LineChartPainter({
    required this.points,
    required this.metric,
    required this.color,
    required this.unit,
    required this.selectedIndex,
  });

  final List<SeriesPoint> points;
  final ChartMetric metric;
  final Color color;
  final String unit;
  final int? selectedIndex;

  @override
  void paint(Canvas canvas, Size size) {
    final chart = _chartRect(size);
    final values = points.map(_value).toList();
    final minValue = values.reduce(math.min);
    final maxValue = values.reduce(math.max);
    final range = math.max(maxValue - minValue, 1.0);
    final minTime = points.first.timestamp.millisecondsSinceEpoch;
    final maxTime = points.last.timestamp.millisecondsSinceEpoch;
    final timeRange = math.max(maxTime - minTime, 1);
    final hasSinglePoint = points.length == 1;

    final gridPaint = Paint()
      ..color = AppColors.border
      ..strokeWidth = 1;
    final axisText = TextStyle(color: AppColors.textMuted, fontSize: 10);

    for (var i = 0; i < 4; i++) {
      final y = chart.top + (chart.height / 3) * i;
      canvas.drawLine(Offset(chart.left, y), Offset(chart.right, y), gridPaint);
      final labelValue = maxValue - (range / 3) * i;
      _drawText(
        canvas,
        '${labelValue.toStringAsFixed(1)}$unit',
        Offset(0, y - 7),
        axisText,
      );
    }

    final path = Path();
    for (var i = 0; i < points.length; i++) {
      final point = points[i];
      final offset = _pointOffset(
        point,
        chart,
        minTime,
        timeRange,
        minValue,
        range,
      );
      if (i == 0) {
        path.moveTo(offset.dx, offset.dy);
      } else {
        path.lineTo(offset.dx, offset.dy);
      }
    }

    final linePaint = Paint()
      ..color = color
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    canvas.drawPath(path, linePaint);

    if (hasSinglePoint) {
      final offset = _pointOffset(
        points.first,
        chart,
        minTime,
        timeRange,
        minValue,
        range,
      );
      _drawPointMarker(canvas, offset, radius: 5);
    } else {
      final fillPath = Path.from(path)
        ..lineTo(chart.right, chart.bottom)
        ..lineTo(chart.left, chart.bottom)
        ..close();
      final fillPaint = Paint()
        ..shader = LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [
            color.withValues(alpha: 0.22),
            color.withValues(alpha: 0.02),
          ],
        ).createShader(chart);
      canvas.drawPath(fillPath, fillPaint);
    }

    _drawText(
      canvas,
      _timeLabel(points.first.timestamp),
      Offset(chart.left, chart.bottom + 8),
      axisText,
    );
    _drawText(
      canvas,
      _timeLabel(points.last.timestamp),
      Offset(chart.right - 38, chart.bottom + 8),
      axisText,
    );

    final index = selectedIndex;
    if (index != null && index >= 0 && index < points.length) {
      _drawSelection(
        canvas,
        chart,
        points[index],
        minTime,
        timeRange,
        minValue,
        range,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _LineChartPainter oldDelegate) {
    return oldDelegate.points != points ||
        oldDelegate.metric != metric ||
        oldDelegate.selectedIndex != selectedIndex;
  }

  double _value(SeriesPoint point) {
    return metric == ChartMetric.temperature
        ? point.temperature
        : point.humidity;
  }

  String _timeLabel(DateTime value) {
    final hour = value.hour.toString().padLeft(2, '0');
    final minute = value.minute.toString().padLeft(2, '0');
    return '$hour:$minute';
  }

  Offset _pointOffset(
    SeriesPoint point,
    Rect chart,
    int minTime,
    int timeRange,
    double minValue,
    double range,
  ) {
    final x = points.length == 1
        ? chart.center.dx
        : chart.left +
              ((point.timestamp.millisecondsSinceEpoch - minTime) / timeRange) *
                  chart.width;
    final y =
        chart.bottom - ((_value(point) - minValue) / range) * chart.height;
    return Offset(x, y);
  }

  void _drawSelection(
    Canvas canvas,
    Rect chart,
    SeriesPoint point,
    int minTime,
    int timeRange,
    double minValue,
    double range,
  ) {
    final offset = _pointOffset(
      point,
      chart,
      minTime,
      timeRange,
      minValue,
      range,
    );
    final guidePaint = Paint()
      ..color = AppColors.textMuted.withValues(alpha: 0.5)
      ..strokeWidth = 1;
    canvas.drawLine(
      Offset(offset.dx, chart.top),
      Offset(offset.dx, chart.bottom),
      guidePaint,
    );

    _drawPointMarker(canvas, offset, radius: 4);

    final label =
        '${_value(point).toStringAsFixed(2)} $unit  ${_timeLabel(point.timestamp)}';
    final textPainter = TextPainter(
      text: TextSpan(
        text: label,
        style: const TextStyle(
          color: AppColors.textPrimary,
          fontSize: 11,
          fontWeight: FontWeight.w700,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();

    const padding = EdgeInsets.symmetric(horizontal: 8, vertical: 5);
    final tooltipWidth = textPainter.width + padding.horizontal;
    final tooltipHeight = textPainter.height + padding.vertical;
    final left = (offset.dx - tooltipWidth / 2).clamp(
      chart.left,
      chart.right - tooltipWidth,
    );
    final top = offset.dy - tooltipHeight - 10 < chart.top
        ? offset.dy + 10
        : offset.dy - tooltipHeight - 10;
    final tooltipRect = RRect.fromRectAndRadius(
      Rect.fromLTWH(left, top, tooltipWidth, tooltipHeight),
      const Radius.circular(6),
    );
    final tooltipPaint = Paint()..color = AppColors.surfaceAlt;
    final tooltipBorderPaint = Paint()
      ..color = color.withValues(alpha: 0.7)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    canvas.drawRRect(tooltipRect, tooltipPaint);
    canvas.drawRRect(tooltipRect, tooltipBorderPaint);
    textPainter.paint(canvas, Offset(left + padding.left, top + padding.top));
  }

  void _drawText(Canvas canvas, String text, Offset offset, TextStyle style) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(canvas, offset);
  }

  void _drawPointMarker(
    Canvas canvas,
    Offset offset, {
    required double radius,
  }) {
    final markerPaint = Paint()..color = color;
    final markerBorderPaint = Paint()..color = AppColors.background;
    canvas.drawCircle(offset, radius + 2, markerBorderPaint);
    canvas.drawCircle(offset, radius, markerPaint);
  }
}
