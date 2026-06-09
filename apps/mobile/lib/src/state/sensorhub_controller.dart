import 'dart:async';

import 'package:flutter/foundation.dart';

import '../api/sensorhub_repository.dart';
import '../models/sensorhub_models.dart';

enum LoadState { idle, loading, ready, error }

enum DetailPeriod {
  oneHour('Última 1h', Duration(hours: 1), '1m'),
  sixHours('Últimas 6h', Duration(hours: 6), '5m'),
  twentyFourHours('Últimas 24h', Duration(hours: 24), '1h'),
  sevenDays('Últimos 7 dias', Duration(days: 7), '1d');

  const DetailPeriod(this.label, this.duration, this.bucket);

  final String label;
  final Duration duration;
  final String bucket;
}

class SensorHubController extends ChangeNotifier {
  SensorHubController(this._repository);

  static const pollInterval = Duration(seconds: 5);

  final SensorHubDataSource _repository;
  Timer? _pollTimer;
  bool _refreshingLatest = false;

  LoadState state = LoadState.idle;
  String? errorMessage;
  AppUser? currentUser;
  List<DashboardDevice> dashboardDevices = const [];
  Map<String, DashboardLatestMeasurement> latestByDevice = const {};
  List<Device> devices = const [];
  List<SensorEnvironment> environments = const [];

  Future<void> loadInitial({bool enablePolling = true}) async {
    state = LoadState.loading;
    errorMessage = null;
    notifyListeners();

    try {
      currentUser = await _repository.loadCurrentUser();
      await Future.wait([
        loadDashboardDevices(notify: false),
        loadDashboardLatest(notify: false),
        loadDevices(notify: false),
        loadEnvironments(notify: false),
      ]);
      state = LoadState.ready;
      if (enablePolling) {
        startPolling();
      }
    } catch (error) {
      state = LoadState.error;
      errorMessage = _message(error);
    }
    notifyListeners();
  }

  void startPolling() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(pollInterval, (_) => loadDashboardLatest());
  }

  void stopPolling() {
    _pollTimer?.cancel();
    _pollTimer = null;
  }

  Future<void> loadDashboardDevices({bool notify = true}) async {
    final user = currentUser;
    if (user == null) {
      return;
    }
    dashboardDevices = await _repository.loadDashboardDevices(user.uuid);
    if (notify) {
      notifyListeners();
    }
  }

  Future<void> loadDashboardLatest({bool notify = true}) async {
    final user = currentUser;
    if (user == null || _refreshingLatest) {
      return;
    }
    _refreshingLatest = true;
    try {
      final latest = await _repository.loadDashboardLatest(user.uuid);
      final deviceStatusByUuid = {
        for (final device in dashboardDevices)
          device.deviceUuid: device.deviceStatus,
      };
      final updated = Map<String, DashboardLatestMeasurement>.from(
        latestByDevice,
      );
      for (final item in latest) {
        final isInactivated =
            deviceStatusByUuid[item.deviceUuid] == 'INACTIVATED';
        if (!isInactivated || !updated.containsKey(item.deviceUuid)) {
          updated[item.deviceUuid] = item;
        }
      }
      latestByDevice = updated;
      if (notify) {
        notifyListeners();
      }
    } finally {
      _refreshingLatest = false;
    }
  }

  Future<void> loadDevices({bool notify = true}) async {
    devices = await _repository.loadDevices();
    if (notify) {
      notifyListeners();
    }
  }

  Future<void> loadEnvironments({bool notify = true}) async {
    environments = await _repository.loadEnvironments();
    if (notify) {
      notifyListeners();
    }
  }

  Future<void> createDevice({
    required String hardwareUuid,
    required String? environmentUuid,
    required String? name,
  }) async {
    final user = currentUser;
    if (user == null) {
      throw StateError('Usuário não carregado.');
    }
    await _repository.createDevice(
      hardwareUuid: hardwareUuid,
      userUuid: user.uuid,
      environmentUuid: environmentUuid,
      name: name,
    );
    await Future.wait([
      loadDashboardDevices(notify: false),
      loadDashboardLatest(notify: false),
      loadDevices(notify: false),
    ]);
    notifyListeners();
  }

  Future<void> updateDevice({
    required String deviceUuid,
    required String? environmentUuid,
    required String? name,
    required String status,
  }) async {
    await _repository.updateDevice(
      deviceUuid: deviceUuid,
      environmentUuid: environmentUuid,
      name: name,
      status: status,
    );
    await loadDashboardDevices(notify: false);
    await Future.wait([
      loadDashboardLatest(notify: false),
      loadDevices(notify: false),
    ]);
    notifyListeners();
  }

  Future<void> inactivateDevice(Device device) {
    return updateDevice(
      deviceUuid: device.uuid,
      environmentUuid: device.environmentUuid,
      name: device.name,
      status: 'INACTIVATED',
    );
  }

  Future<void> reactivateDevice(Device device) {
    return updateDevice(
      deviceUuid: device.uuid,
      environmentUuid: device.environmentUuid,
      name: device.name,
      status: 'ACTIVATED',
    );
  }

  Future<void> deleteDevice(String deviceUuid) async {
    await _repository.deleteDevice(deviceUuid);
    await Future.wait([
      loadDashboardDevices(notify: false),
      loadDashboardLatest(notify: false),
      loadDevices(notify: false),
    ]);
    latestByDevice = Map<String, DashboardLatestMeasurement>.from(
      latestByDevice,
    )..remove(deviceUuid);
    notifyListeners();
  }

  Future<void> createEnvironment(String name) async {
    final user = currentUser;
    if (user == null) {
      throw StateError('Usuário não carregado.');
    }
    await _repository.createEnvironment(userUuid: user.uuid, name: name);
    await loadEnvironments(notify: false);
    await loadDashboardDevices(notify: false);
    notifyListeners();
  }

  Future<void> updateEnvironment({
    required String environmentUuid,
    required String name,
  }) async {
    await _repository.updateEnvironment(
      environmentUuid: environmentUuid,
      name: name,
    );
    await loadEnvironments(notify: false);
    await loadDashboardDevices(notify: false);
    notifyListeners();
  }

  Future<void> deleteEnvironment(String environmentUuid) async {
    await _repository.deleteEnvironment(environmentUuid);
    await loadEnvironments(notify: false);
    await loadDashboardDevices(notify: false);
    notifyListeners();
  }

  Future<void> updateUser({required String name}) async {
    final user = currentUser;
    if (user == null) {
      throw StateError('Usuário não carregado.');
    }
    currentUser = await _repository.updateUser(
      userUuid: user.uuid,
      name: name,
      email: user.email,
    );
    notifyListeners();
  }

  Future<MeasurementOverview> loadOverview({
    required String deviceUuid,
    required DetailPeriod period,
  }) {
    final to = DateTime.now().toUtc();
    return _repository.loadMeasurementOverview(
      deviceUuid: deviceUuid,
      from: to.subtract(period.duration),
      to: to,
      bucket: period.bucket,
    );
  }

  List<SensorCardData> sensorCards() {
    return dashboardDevices
        .map(
          (device) => SensorCardData(
            device: device,
            latest: latestByDevice[device.deviceUuid],
          ),
        )
        .toList();
  }

  String environmentName(String? environmentUuid) {
    if (environmentUuid == null) {
      return 'Sem ambiente';
    }
    for (final environment in environments) {
      if (environment.uuid == environmentUuid) {
        return environment.name;
      }
    }
    return 'Ambiente não encontrado';
  }

  @override
  void dispose() {
    stopPolling();
    super.dispose();
  }

  String _message(Object error) {
    final text = error.toString();
    if (text.contains('Connection refused') ||
        text.contains('SocketException')) {
      return 'Não foi possível conectar à API.';
    }
    return text.replaceFirst('Exception: ', '');
  }
}
