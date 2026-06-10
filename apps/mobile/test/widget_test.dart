import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sensorhub_mobile/main.dart';
import 'package:sensorhub_mobile/src/api/sensorhub_repository.dart';
import 'package:sensorhub_mobile/src/models/sensorhub_models.dart';
import 'package:sensorhub_mobile/src/state/sensorhub_controller.dart';
import 'package:sensorhub_mobile/src/widgets/measurement_chart.dart';

void main() {
  testWidgets('renders dashboard card and opens measurement overview', (
    tester,
  ) async {
    await tester.pumpWidget(
      SensorHubApp(repository: FakeSensorHubDataSource(), enablePolling: false),
    );

    await tester.pumpAndSettle();

    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Text &&
            RegExp(
              r'^(Bom dia|Boa tarde|Boa noite), Admin!$',
            ).hasMatch(widget.data ?? ''),
      ),
      findsOneWidget,
    );
    expect(find.text('Sensor da sala'), findsOneWidget);
    expect(find.text('24.7 °C'), findsOneWidget);
    expect(find.text('58.2 %'), findsOneWidget);

    await tester.tap(find.text('Sensor da sala'));
    await tester.pumpAndSettle();

    expect(find.text('Temperatura'), findsWidgets);
    expect(find.text('Umidade'), findsWidgets);
    await tester.tap(find.byType(MeasurementChart).first);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);

    await tester.drag(find.byType(Scrollable).last, const Offset(0, -900));
    await tester.pumpAndSettle();

    expect(find.text('Overview do período'), findsOneWidget);
    expect(find.text('Medições consideradas'), findsOneWidget);
  });

  testWidgets('profile shows only name and read-only email', (tester) async {
    await tester.pumpWidget(
      SensorHubApp(repository: FakeSensorHubDataSource(), enablePolling: false),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Perfil'));
    await tester.pumpAndSettle();

    expect(find.text('Admin'), findsOneWidget);
    expect(find.text('admin@sensorhub.com'), findsOneWidget);
    expect(find.text(FakeSensorHubDataSource.user.uuid), findsNothing);

    final emailField = tester.widget<EditableText>(
      find.byWidgetPredicate(
        (widget) =>
            widget is EditableText &&
            widget.controller.text == 'admin@sensorhub.com',
      ),
    );
    expect(emailField.readOnly, isTrue);
  });

  testWidgets('device deletion requires confirmation', (tester) async {
    final repository = FakeSensorHubDataSource();
    await tester.pumpWidget(
      SensorHubApp(repository: repository, enablePolling: false),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Dispositivos'));
    await tester.pumpAndSettle();
    expect(find.text('Ativo'), findsOneWidget);
    await tester.tap(find.text('Sensor da sala'));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Excluir dispositivo'));
    await tester.pumpAndSettle();

    expect(find.text('Excluir dispositivo?'), findsOneWidget);
    expect(
      find.textContaining('Todas as medições relacionadas'),
      findsOneWidget,
    );

    await tester.tap(find.text('Cancelar'));
    await tester.pumpAndSettle();
    expect(repository.deletedDeviceCount, 0);

    await tester.tap(find.text('Excluir dispositivo'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(FilledButton, 'Excluir'));
    await tester.pumpAndSettle();

    expect(repository.deletedDeviceCount, 1);
  });

  testWidgets('inactivated device shows reactivate action with play icon', (
    tester,
  ) async {
    await tester.pumpWidget(
      SensorHubApp(
        repository: FakeSensorHubDataSource(deviceStatus: 'INACTIVATED'),
        enablePolling: false,
      ),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Dispositivos'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sensor da sala'));
    await tester.pumpAndSettle();

    expect(find.text('Reativar dispositivo'), findsOneWidget);
    expect(find.byIcon(Icons.play_circle), findsOneWidget);
    expect(find.text('Inativar dispositivo'), findsNothing);
  });

  testWidgets('detail screen refreshes latest measurement while polling', (
    tester,
  ) async {
    final repository = FakeSensorHubDataSource(
      overviewTemperatures: const [24.7, 25.8],
      overviewHumidities: const [58.2, 61.4],
    );
    await tester.pumpWidget(
      SensorHubApp(repository: repository, enablePolling: true),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Sensor da sala'));
    await tester.pumpAndSettle();

    expect(find.text('24.7 °C'), findsWidgets);
    expect(find.text('58.2 %'), findsWidgets);

    await tester.pump(SensorHubController.pollInterval);
    await tester.pumpAndSettle();

    expect(find.text('25.8 °C'), findsOneWidget);
    expect(find.text('61.4 %'), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('detail screen pull-to-refresh updates latest measurement', (
    tester,
  ) async {
    final repository = FakeSensorHubDataSource(
      overviewTemperatures: const [24.7, 26.1],
      overviewHumidities: const [58.2, 62.5],
    );
    await tester.pumpWidget(
      SensorHubApp(repository: repository, enablePolling: false),
    );

    await tester.pumpAndSettle();
    await tester.tap(find.text('Sensor da sala'));
    await tester.pumpAndSettle();

    expect(find.text('24.7 °C'), findsWidgets);
    expect(find.text('58.2 %'), findsWidgets);

    await tester.drag(find.byType(Scrollable).last, const Offset(0, 500));
    await tester.pumpAndSettle();

    expect(find.text('26.1 °C'), findsOneWidget);
    expect(find.text('62.5 %'), findsOneWidget);
  });
}

class FakeSensorHubDataSource implements SensorHubDataSource {
  FakeSensorHubDataSource({
    this.deviceStatus = 'ACTIVATED',
    this.overviewTemperatures = const [24.7],
    this.overviewHumidities = const [58.2],
  });

  final String deviceStatus;
  final List<double> overviewTemperatures;
  final List<double> overviewHumidities;

  static const user = AppUser(
    uuid: '11111111-1111-4111-8111-111111111111',
    name: 'Admin',
    email: 'admin@sensorhub.com',
  );

  static const deviceUuid = '22222222-2222-4222-8222-222222222222';
  static const environmentUuid = '33333333-3333-4333-8333-333333333333';
  int deletedDeviceCount = 0;
  int overviewLoadCount = 0;

  @override
  Future<AppUser> loadCurrentUser() async => user;

  @override
  Future<List<DashboardDevice>> loadDashboardDevices(String userUuid) async {
    return [
      DashboardDevice(
        deviceUuid: deviceUuid,
        hardwareUuid: 'b0fee3a6-ae91-4265-9365-36f793f32f06',
        name: 'Sensor da sala',
        environmentUuid: environmentUuid,
        environmentName: 'Sala',
        deviceStatus: deviceStatus,
      ),
    ];
  }

  @override
  Future<List<DashboardLatestMeasurement>> loadDashboardLatest(
    String userUuid,
  ) async {
    return [
      DashboardLatestMeasurement(
        deviceUuid: deviceUuid,
        freshnessStatus: 'ONLINE',
        lastSeenAt: DateTime(2026, 6, 4, 18, 15),
        latestMeasurement: LatestMeasurement(
          temperature: 24.7,
          temperatureUnit: 'CELSIUS',
          humidity: 58.2,
          humidityUnit: 'RELATIVE_PERCENT',
          measuredAt: DateTime(2026, 6, 4, 18, 15),
        ),
      ),
    ];
  }

  @override
  Future<List<Device>> loadDevices() async {
    return [
      Device(
        uuid: deviceUuid,
        hardwareUuid: 'b0fee3a6-ae91-4265-9365-36f793f32f06',
        userUuid: user.uuid,
        environmentUuid: environmentUuid,
        status: deviceStatus,
        name: 'Sensor da sala',
      ),
    ];
  }

  @override
  Future<List<SensorEnvironment>> loadEnvironments() async {
    return [
      SensorEnvironment(
        uuid: environmentUuid,
        userUuid: user.uuid,
        name: 'Sala',
      ),
    ];
  }

  @override
  Future<Device> createDevice({
    required String hardwareUuid,
    required String userUuid,
    required String? environmentUuid,
    required String? name,
  }) async {
    return Device(
      uuid: deviceUuid,
      hardwareUuid: hardwareUuid,
      userUuid: userUuid,
      environmentUuid: environmentUuid,
      status: 'ACTIVATED',
      name: name,
    );
  }

  @override
  Future<Device> updateDevice({
    required String deviceUuid,
    required String? environmentUuid,
    required String? name,
    required String status,
  }) async {
    return Device(
      uuid: deviceUuid,
      hardwareUuid: 'b0fee3a6-ae91-4265-9365-36f793f32f06',
      userUuid: user.uuid,
      environmentUuid: environmentUuid,
      status: status,
      name: name,
    );
  }

  @override
  Future<void> deleteDevice(String deviceUuid) async {
    deletedDeviceCount += 1;
  }

  @override
  Future<SensorEnvironment> createEnvironment({
    required String userUuid,
    required String name,
  }) async {
    return SensorEnvironment(
      uuid: environmentUuid,
      userUuid: userUuid,
      name: name,
    );
  }

  @override
  Future<SensorEnvironment> updateEnvironment({
    required String environmentUuid,
    required String name,
  }) async {
    return SensorEnvironment(
      uuid: environmentUuid,
      userUuid: user.uuid,
      name: name,
    );
  }

  @override
  Future<void> deleteEnvironment(String environmentUuid) async {}

  @override
  Future<AppUser> updateUser({
    required String userUuid,
    required String name,
    required String email,
  }) async {
    return AppUser(uuid: userUuid, name: name, email: email);
  }

  @override
  Future<MeasurementOverview> loadMeasurementOverview({
    required String deviceUuid,
    required DateTime from,
    required DateTime to,
    required String bucket,
  }) async {
    final readingIndex = overviewLoadCount < overviewTemperatures.length
        ? overviewLoadCount
        : overviewTemperatures.length - 1;
    overviewLoadCount += 1;
    final temperature = overviewTemperatures[readingIndex];
    final humidity = overviewHumidities[readingIndex];
    final measuredAt = DateTime(2026, 6, 4, 18, 15);
    final previousMeasuredAt = DateTime(2026, 6, 4, 18, 10);
    final temperatureMax = temperature >= 24.5 ? temperature : 24.5;
    final temperatureMin = temperature < 24.5 ? temperature : 24.5;
    final humidityMax = humidity >= 59.1 ? humidity : 59.1;
    final humidityMin = humidity < 59.1 ? humidity : 59.1;
    return MeasurementOverview(
      deviceUuid: deviceUuid,
      freshnessStatus: 'ONLINE',
      lastSeenAt: measuredAt,
      period: OverviewPeriod(from: from, to: to, bucket: bucket),
      latestMeasurement: LatestMeasurement(
        temperature: temperature,
        temperatureUnit: 'CELSIUS',
        humidity: humidity,
        humidityUnit: 'RELATIVE_PERCENT',
        measuredAt: measuredAt,
      ),
      series: [
        SeriesPoint(
          timestamp: previousMeasuredAt,
          temperature: 24.5,
          humidity: 59.1,
        ),
        SeriesPoint(
          timestamp: measuredAt,
          temperature: temperature,
          humidity: humidity,
        ),
      ],
      overview: OverviewStats(
        temperatureMax: temperatureMax,
        temperatureMaxAt: temperature >= 24.5 ? measuredAt : previousMeasuredAt,
        temperatureMin: temperatureMin,
        temperatureMinAt: temperature < 24.5 ? measuredAt : previousMeasuredAt,
        temperatureAverage: (24.5 + temperature) / 2,
        humidityMax: humidityMax,
        humidityMaxAt: humidity >= 59.1 ? measuredAt : previousMeasuredAt,
        humidityMin: humidityMin,
        humidityMinAt: humidity < 59.1 ? measuredAt : previousMeasuredAt,
        humidityAverage: (59.1 + humidity) / 2,
        measurementCount: 2,
      ),
    );
  }
}
