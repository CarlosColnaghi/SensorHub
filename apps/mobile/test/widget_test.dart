import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:sensorhub_mobile/main.dart';
import 'package:sensorhub_mobile/src/api/sensorhub_repository.dart';
import 'package:sensorhub_mobile/src/models/sensorhub_models.dart';

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
}

class FakeSensorHubDataSource implements SensorHubDataSource {
  FakeSensorHubDataSource({this.deviceStatus = 'ACTIVATED'});

  final String deviceStatus;

  static const user = AppUser(
    uuid: '11111111-1111-4111-8111-111111111111',
    name: 'Admin',
    email: 'admin@sensorhub.com',
  );

  static const deviceUuid = '22222222-2222-4222-8222-222222222222';
  static const environmentUuid = '33333333-3333-4333-8333-333333333333';
  int deletedDeviceCount = 0;

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
    return MeasurementOverview(
      deviceUuid: deviceUuid,
      freshnessStatus: 'ONLINE',
      lastSeenAt: DateTime(2026, 6, 4, 18, 15),
      period: OverviewPeriod(from: from, to: to, bucket: bucket),
      latestMeasurement: LatestMeasurement(
        temperature: 24.7,
        temperatureUnit: 'CELSIUS',
        humidity: 58.2,
        humidityUnit: 'RELATIVE_PERCENT',
        measuredAt: DateTime(2026, 6, 4, 18, 15),
      ),
      series: [
        SeriesPoint(
          timestamp: DateTime(2026, 6, 4, 18, 10),
          temperature: 24.5,
          humidity: 59.1,
        ),
        SeriesPoint(
          timestamp: DateTime(2026, 6, 4, 18, 15),
          temperature: 24.7,
          humidity: 58.2,
        ),
      ],
      overview: OverviewStats(
        temperatureMax: 24.7,
        temperatureMaxAt: DateTime(2026, 6, 4, 18, 15),
        temperatureMin: 24.5,
        temperatureMinAt: DateTime(2026, 6, 4, 18, 10),
        temperatureAverage: 24.6,
        humidityMax: 59.1,
        humidityMaxAt: DateTime(2026, 6, 4, 18, 10),
        humidityMin: 58.2,
        humidityMinAt: DateTime(2026, 6, 4, 18, 15),
        humidityAverage: 58.7,
        measurementCount: 2,
      ),
    );
  }
}
