import '../models/sensorhub_models.dart';
import 'sensorhub_api_client.dart';

abstract class SensorHubDataSource {
  Future<AppUser> loadCurrentUser();
  Future<List<DashboardDevice>> loadDashboardDevices(String userUuid);
  Future<List<DashboardLatestMeasurement>> loadDashboardLatest(String userUuid);
  Future<List<Device>> loadDevices();
  Future<List<SensorEnvironment>> loadEnvironments();
  Future<Device> createDevice({
    required String hardwareUuid,
    required String userUuid,
    required String? environmentUuid,
    required String? name,
  });
  Future<Device> updateDevice({
    required String deviceUuid,
    required String? environmentUuid,
    required String? name,
    required String status,
  });
  Future<void> deleteDevice(String deviceUuid);
  Future<SensorEnvironment> createEnvironment({
    required String userUuid,
    required String name,
  });
  Future<SensorEnvironment> updateEnvironment({
    required String environmentUuid,
    required String name,
  });
  Future<void> deleteEnvironment(String environmentUuid);
  Future<AppUser> updateUser({
    required String userUuid,
    required String name,
    required String email,
  });
  Future<MeasurementOverview> loadMeasurementOverview({
    required String deviceUuid,
    required DateTime from,
    required DateTime to,
    required String bucket,
  });
}

class SensorHubRepository implements SensorHubDataSource {
  const SensorHubRepository(this._api);

  final SensorHubApiClient _api;

  @override
  Future<AppUser> loadCurrentUser() async {
    final response = await _api.get('/api/v1/users');
    final users = _asList(response).map(AppUser.fromJson).toList();
    if (users.isEmpty) {
      throw const ApiException(404, 'Nenhum usuário cadastrado na API.');
    }
    return users.firstWhere(
      (user) => user.email == 'admin@sensorhub.com',
      orElse: () => users.first,
    );
  }

  @override
  Future<List<DashboardDevice>> loadDashboardDevices(String userUuid) async {
    final response = await _api.get(
      '/api/v1/users/$userUuid/dashboard/devices',
    );
    return _asList(response).map(DashboardDevice.fromJson).toList();
  }

  @override
  Future<List<DashboardLatestMeasurement>> loadDashboardLatest(
    String userUuid,
  ) async {
    final response = await _api.get(
      '/api/v1/users/$userUuid/dashboard/measurements/latest',
    );
    return _asList(response).map(DashboardLatestMeasurement.fromJson).toList();
  }

  @override
  Future<List<Device>> loadDevices() async {
    final response = await _api.get('/api/v1/devices');
    return _asList(response).map(Device.fromJson).toList();
  }

  @override
  Future<List<SensorEnvironment>> loadEnvironments() async {
    final response = await _api.get('/api/v1/environments');
    return _asList(response).map(SensorEnvironment.fromJson).toList();
  }

  @override
  Future<Device> createDevice({
    required String hardwareUuid,
    required String userUuid,
    required String? environmentUuid,
    required String? name,
  }) async {
    final response = await _api.post('/api/v1/devices', {
      'hardwareUuid': hardwareUuid,
      'userUuid': userUuid,
      'environmentUuid': environmentUuid,
      'status': 'ACTIVATED',
      'name': name,
    });
    return Device.fromJson(_asMap(response));
  }

  @override
  Future<Device> updateDevice({
    required String deviceUuid,
    required String? environmentUuid,
    required String? name,
    required String status,
  }) async {
    final response = await _api.put('/api/v1/devices/$deviceUuid', {
      'environmentUuid': environmentUuid,
      'status': status,
      'name': name,
    });
    return Device.fromJson(_asMap(response));
  }

  @override
  Future<void> deleteDevice(String deviceUuid) async {
    await _api.delete('/api/v1/devices/$deviceUuid');
  }

  @override
  Future<SensorEnvironment> createEnvironment({
    required String userUuid,
    required String name,
  }) async {
    final response = await _api.post('/api/v1/environments', {
      'userUuid': userUuid,
      'name': name,
    });
    return SensorEnvironment.fromJson(_asMap(response));
  }

  @override
  Future<SensorEnvironment> updateEnvironment({
    required String environmentUuid,
    required String name,
  }) async {
    final response = await _api.put('/api/v1/environments/$environmentUuid', {
      'name': name,
    });
    return SensorEnvironment.fromJson(_asMap(response));
  }

  @override
  Future<void> deleteEnvironment(String environmentUuid) async {
    await _api.delete('/api/v1/environments/$environmentUuid');
  }

  @override
  Future<AppUser> updateUser({
    required String userUuid,
    required String name,
    required String email,
  }) async {
    final response = await _api.put('/api/v1/users/$userUuid', {
      'name': name,
      'email': email,
    });
    return AppUser.fromJson(_asMap(response));
  }

  @override
  Future<MeasurementOverview> loadMeasurementOverview({
    required String deviceUuid,
    required DateTime from,
    required DateTime to,
    required String bucket,
  }) async {
    final response = await _api.get(
      '/api/v1/devices/$deviceUuid/measurements/overview',
      query: {
        'from': from.toUtc().toIso8601String(),
        'to': to.toUtc().toIso8601String(),
        'bucket': bucket,
      },
    );
    return MeasurementOverview.fromJson(_asMap(response));
  }

  List<Map<String, dynamic>> _asList(Object? value) {
    if (value is! List) {
      return const [];
    }
    return value.whereType<Map<String, dynamic>>().toList();
  }

  Map<String, dynamic> _asMap(Object? value) {
    if (value is Map<String, dynamic>) {
      return value;
    }
    throw const ApiException(500, 'Resposta inesperada da API.');
  }
}
