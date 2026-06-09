import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../models/sensorhub_models.dart';
import '../theme/app_theme.dart';

enum ChartMetric { temperature, humidity }

class MeasurementChart extends StatelessWidget {
  const MeasurementChart({
    required this.points,
    required this.metric,
    super.key,
  });

  final List<SeriesPoint> points;
  final ChartMetric metric;

  @override
  Widget build(BuildContext context) {
    final color = metric == ChartMetric.temperature
        ? AppColors.temperature
        : AppColors.humidity;
    final title = metric == ChartMetric.temperature ? 'Temperatura' : 'Umidade';
    final unit = metric == ChartMetric.temperature ? '°C' : '%';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  metric == ChartMetric.temperature
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
              child: points.isEmpty
                  ? const Center(child: Text('Sem dados no período'))
                  : CustomPaint(
                      painter: _LineChartPainter(
                        points: points,
                        metric: metric,
                        color: color,
                        unit: unit,
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _LineChartPainter extends CustomPainter {
  const _LineChartPainter({
    required this.points,
    required this.metric,
    required this.color,
    required this.unit,
  });

  final List<SeriesPoint> points;
  final ChartMetric metric;
  final Color color;
  final String unit;

  @override
  void paint(Canvas canvas, Size size) {
    const left = 42.0;
    const right = 12.0;
    const top = 14.0;
    const bottom = 30.0;
    final chart = Rect.fromLTRB(
      left,
      top,
      size.width - right,
      size.height - bottom,
    );
    final values = points.map(_value).toList();
    final minValue = values.reduce(math.min);
    final maxValue = values.reduce(math.max);
    final range = math.max(maxValue - minValue, 1.0);
    final minTime = points.first.timestamp.millisecondsSinceEpoch;
    final maxTime = points.last.timestamp.millisecondsSinceEpoch;
    final timeRange = math.max(maxTime - minTime, 1);

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
      final x =
          chart.left +
          ((point.timestamp.millisecondsSinceEpoch - minTime) / timeRange) *
              chart.width;
      final y =
          chart.bottom - ((_value(point) - minValue) / range) * chart.height;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }

    final linePaint = Paint()
      ..color = color
      ..strokeWidth = 2.5
      ..style = PaintingStyle.stroke
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    canvas.drawPath(path, linePaint);

    final fillPath = Path.from(path)
      ..lineTo(chart.right, chart.bottom)
      ..lineTo(chart.left, chart.bottom)
      ..close();
    final fillPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [color.withValues(alpha: 0.22), color.withValues(alpha: 0.02)],
      ).createShader(chart);
    canvas.drawPath(fillPath, fillPaint);

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
  }

  @override
  bool shouldRepaint(covariant _LineChartPainter oldDelegate) {
    return oldDelegate.points != points || oldDelegate.metric != metric;
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

  void _drawText(Canvas canvas, String text, Offset offset, TextStyle style) {
    final painter = TextPainter(
      text: TextSpan(text: text, style: style),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(canvas, offset);
  }
}
