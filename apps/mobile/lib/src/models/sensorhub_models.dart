class AppUser {
  const AppUser({required this.uuid, required this.name, required this.email});

  final String uuid;
  final String name;
  final String email;

  factory AppUser.fromJson(Map<String, dynamic> json) {
    return AppUser(
      uuid: json['uuid'] as String,
      name: json['name'] as String? ?? '',
      email: json['email'] as String? ?? '',
    );
  }
}

class SensorEnvironment {
  const SensorEnvironment({
    required this.uuid,
    required this.userUuid,
    required this.name,
  });

  final String uuid;
  final String userUuid;
  final String name;

  factory SensorEnvironment.fromJson(Map<String, dynamic> json) {
    return SensorEnvironment(
      uuid: json['uuid'] as String,
      userUuid: json['userUuid'] as String,
      name: json['name'] as String? ?? '',
    );
  }
}

class Device {
  const Device({
    required this.uuid,
    required this.hardwareUuid,
    required this.userUuid,
    required this.environmentUuid,
    required this.status,
    required this.name,
  });

  final String uuid;
  final String hardwareUuid;
  final String userUuid;
  final String? environmentUuid;
  final String status;
  final String? name;

  factory Device.fromJson(Map<String, dynamic> json) {
    return Device(
      uuid: json['uuid'] as String,
      hardwareUuid: json['hardwareUuid'] as String,
      userUuid: json['userUuid'] as String,
      environmentUuid: json['environmentUuid'] as String?,
      status: json['status'] as String? ?? 'ACTIVATED',
      name: json['name'] as String?,
    );
  }
}

class DashboardDevice {
  const DashboardDevice({
    required this.deviceUuid,
    required this.hardwareUuid,
    required this.name,
    required this.environmentUuid,
    required this.environmentName,
    required this.deviceStatus,
  });

  final String deviceUuid;
  final String hardwareUuid;
  final String? name;
  final String? environmentUuid;
  final String? environmentName;
  final String deviceStatus;

  factory DashboardDevice.fromJson(Map<String, dynamic> json) {
    return DashboardDevice(
      deviceUuid: json['deviceUuid'] as String,
      hardwareUuid: json['hardwareUuid'] as String,
      name: json['name'] as String?,
      environmentUuid: json['environmentUuid'] as String?,
      environmentName: json['environmentName'] as String?,
      deviceStatus: json['deviceStatus'] as String? ?? 'ACTIVATED',
    );
  }
}

class LatestMeasurement {
  const LatestMeasurement({
    required this.temperature,
    required this.temperatureUnit,
    required this.humidity,
    required this.humidityUnit,
    required this.measuredAt,
  });

  final double temperature;
  final String temperatureUnit;
  final double humidity;
  final String humidityUnit;
  final DateTime measuredAt;

  factory LatestMeasurement.fromJson(Map<String, dynamic> json) {
    return LatestMeasurement(
      temperature: numberAsDouble(json['temperature']),
      temperatureUnit: json['temperatureUnit'] as String? ?? 'CELSIUS',
      humidity: numberAsDouble(json['humidity']),
      humidityUnit: json['humidityUnit'] as String? ?? 'RELATIVE_PERCENT',
      measuredAt:
          parseDate(json['measuredAt']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
    );
  }
}

class DashboardLatestMeasurement {
  const DashboardLatestMeasurement({
    required this.deviceUuid,
    required this.freshnessStatus,
    required this.lastSeenAt,
    required this.latestMeasurement,
  });

  final String deviceUuid;
  final String freshnessStatus;
  final DateTime? lastSeenAt;
  final LatestMeasurement? latestMeasurement;

  factory DashboardLatestMeasurement.fromJson(Map<String, dynamic> json) {
    final latest = json['latestMeasurement'];
    return DashboardLatestMeasurement(
      deviceUuid: json['deviceUuid'] as String,
      freshnessStatus: json['freshnessStatus'] as String? ?? 'NO_DATA',
      lastSeenAt: parseDate(json['lastSeenAt']),
      latestMeasurement: latest is Map<String, dynamic>
          ? LatestMeasurement.fromJson(latest)
          : null,
    );
  }
}

class SensorCardData {
  const SensorCardData({required this.device, required this.latest});

  final DashboardDevice device;
  final DashboardLatestMeasurement? latest;

  String get title => isBlank(device.name) ? 'Sensor sem nome' : device.name!;
  String get location => isBlank(device.environmentName)
      ? 'Sem ambiente'
      : device.environmentName!;
  String get freshnessStatus => device.deviceStatus == 'INACTIVATED'
      ? 'INACTIVATED'
      : latest?.freshnessStatus ?? 'NO_DATA';
  LatestMeasurement? get measurement => latest?.latestMeasurement;
  DateTime? get lastSeenAt => latest?.lastSeenAt;
}

class MeasurementOverview {
  const MeasurementOverview({
    required this.deviceUuid,
    required this.freshnessStatus,
    required this.lastSeenAt,
    required this.period,
    required this.latestMeasurement,
    required this.series,
    required this.overview,
  });

  final String deviceUuid;
  final String freshnessStatus;
  final DateTime? lastSeenAt;
  final OverviewPeriod period;
  final LatestMeasurement? latestMeasurement;
  final List<SeriesPoint> series;
  final OverviewStats? overview;

  factory MeasurementOverview.fromJson(Map<String, dynamic> json) {
    final latest = json['latestMeasurement'];
    final overview = json['overview'];
    return MeasurementOverview(
      deviceUuid: json['deviceUuid'] as String,
      freshnessStatus: json['freshnessStatus'] as String? ?? 'NO_DATA',
      lastSeenAt: parseDate(json['lastSeenAt']),
      period: OverviewPeriod.fromJson(json['period'] as Map<String, dynamic>),
      latestMeasurement: latest is Map<String, dynamic>
          ? LatestMeasurement.fromJson(latest)
          : null,
      series: jsonList(json['series']).map(SeriesPoint.fromJson).toList(),
      overview: overview is Map<String, dynamic>
          ? OverviewStats.fromJson(overview)
          : null,
    );
  }
}

class OverviewPeriod {
  const OverviewPeriod({
    required this.from,
    required this.to,
    required this.bucket,
  });

  final DateTime from;
  final DateTime to;
  final String bucket;

  factory OverviewPeriod.fromJson(Map<String, dynamic> json) {
    return OverviewPeriod(
      from:
          parseDate(json['from']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      to:
          parseDate(json['to']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      bucket: json['bucket'] as String? ?? 'raw',
    );
  }
}

class SeriesPoint {
  const SeriesPoint({
    required this.timestamp,
    required this.temperature,
    required this.humidity,
  });

  final DateTime timestamp;
  final double temperature;
  final double humidity;

  factory SeriesPoint.fromJson(Map<String, dynamic> json) {
    return SeriesPoint(
      timestamp:
          parseDate(json['timestamp']) ??
          DateTime.fromMillisecondsSinceEpoch(0, isUtc: true),
      temperature: numberAsDouble(json['temperature']),
      humidity: numberAsDouble(json['humidity']),
    );
  }
}

class OverviewStats {
  const OverviewStats({
    required this.temperatureMax,
    required this.temperatureMaxAt,
    required this.temperatureMin,
    required this.temperatureMinAt,
    required this.temperatureAverage,
    required this.humidityMax,
    required this.humidityMaxAt,
    required this.humidityMin,
    required this.humidityMinAt,
    required this.humidityAverage,
    required this.measurementCount,
  });

  final double temperatureMax;
  final DateTime? temperatureMaxAt;
  final double temperatureMin;
  final DateTime? temperatureMinAt;
  final double temperatureAverage;
  final double humidityMax;
  final DateTime? humidityMaxAt;
  final double humidityMin;
  final DateTime? humidityMinAt;
  final double humidityAverage;
  final int measurementCount;

  factory OverviewStats.fromJson(Map<String, dynamic> json) {
    return OverviewStats(
      temperatureMax: numberAsDouble(json['temperatureMax']),
      temperatureMaxAt: parseDate(json['temperatureMaxAt']),
      temperatureMin: numberAsDouble(json['temperatureMin']),
      temperatureMinAt: parseDate(json['temperatureMinAt']),
      temperatureAverage: numberAsDouble(json['temperatureAverage']),
      humidityMax: numberAsDouble(json['humidityMax']),
      humidityMaxAt: parseDate(json['humidityMaxAt']),
      humidityMin: numberAsDouble(json['humidityMin']),
      humidityMinAt: parseDate(json['humidityMinAt']),
      humidityAverage: numberAsDouble(json['humidityAverage']),
      measurementCount: (json['measurementCount'] as num?)?.toInt() ?? 0,
    );
  }
}

DateTime? parseDate(Object? value) {
  if (value is! String || value.isEmpty) {
    return null;
  }
  return DateTime.tryParse(value)?.toLocal();
}

double numberAsDouble(Object? value) {
  if (value is num) {
    return value.toDouble();
  }
  if (value is String) {
    return double.tryParse(value) ?? 0;
  }
  return 0;
}

List<Map<String, dynamic>> jsonList(Object? value) {
  if (value is! List) {
    return const [];
  }
  return value.whereType<Map<String, dynamic>>().toList();
}

bool isBlank(String? value) => value == null || value.trim().isEmpty;
