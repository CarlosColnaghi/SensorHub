import 'package:flutter/material.dart';

import 'src/api/sensorhub_api_client.dart';
import 'src/api/sensorhub_repository.dart';
import 'src/state/sensorhub_controller.dart';
import 'src/theme/app_theme.dart';
import 'src/ui/sensorhub_shell.dart';

void main() {
  const baseUrl = String.fromEnvironment(
    'SENSORHUB_API_BASE_URL',
    defaultValue: 'http://10.0.2.2:8080',
  );

  runApp(
    SensorHubApp(
      repository: SensorHubRepository(SensorHubApiClient(baseUrl: baseUrl)),
    ),
  );
}

class SensorHubApp extends StatelessWidget {
  const SensorHubApp({
    required this.repository,
    this.enablePolling = true,
    super.key,
  });

  final SensorHubDataSource repository;
  final bool enablePolling;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'SensorHub',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.dark(),
      home: SensorHubShell(
        controller: SensorHubController(repository),
        enablePolling: enablePolling,
      ),
    );
  }
}
