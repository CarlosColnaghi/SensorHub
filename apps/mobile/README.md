# SensorHub Mobile

Flutter app for SensorHub.

## Run

Android emulator:

```bash
flutter run --dart-define=SENSORHUB_API_BASE_URL=http://10.0.2.2:8080
```

Linux desktop:

```bash
flutter run -d linux --dart-define=SENSORHUB_API_BASE_URL=http://localhost:8080
```

The app expects the API to be available and uses the first user returned by
`GET /api/v1/users`, preferring `admin@sensorhub.com` when present.

## Docker Compose check

```bash
docker compose --profile mobile run --rm mobile
```
