import 'dart:convert';
import 'dart:io';

class ApiException implements Exception {
  const ApiException(this.statusCode, this.message);

  final int statusCode;
  final String message;

  @override
  String toString() => 'ApiException($statusCode): $message';
}

class SensorHubApiClient {
  SensorHubApiClient({required String baseUrl, HttpClient? httpClient})
    : _baseUri = Uri.parse(baseUrl),
      _httpClient = httpClient ?? HttpClient();

  final Uri _baseUri;
  final HttpClient _httpClient;

  Future<Object?> get(String path, {Map<String, String?> query = const {}}) {
    return _send('GET', path, query: query);
  }

  Future<Object?> post(String path, Map<String, Object?> body) {
    return _send('POST', path, body: body);
  }

  Future<Object?> put(String path, Map<String, Object?> body) {
    return _send('PUT', path, body: body);
  }

  Future<Object?> delete(String path) {
    return _send('DELETE', path);
  }

  Future<Object?> _send(
    String method,
    String path, {
    Map<String, String?> query = const {},
    Map<String, Object?>? body,
  }) async {
    final uri = _uri(path, query);
    final request = await _httpClient.openUrl(method, uri);
    request.headers.contentType = ContentType.json;
    request.headers.set(HttpHeaders.acceptHeader, ContentType.json.mimeType);
    if (body != null) {
      request.write(jsonEncode(body));
    }

    final response = await request.close();
    final responseBody = await response.transform(utf8.decoder).join();
    if (response.statusCode == HttpStatus.noContent) {
      return null;
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw ApiException(response.statusCode, _errorMessage(responseBody));
    }
    if (responseBody.isEmpty) {
      return null;
    }
    return jsonDecode(responseBody) as Object?;
  }

  Uri _uri(String path, Map<String, String?> query) {
    final cleanQuery = <String, String>{};
    for (final entry in query.entries) {
      if (entry.value != null && entry.value!.isNotEmpty) {
        cleanQuery[entry.key] = entry.value!;
      }
    }
    return _baseUri.replace(
      path: '${_baseUri.path}$path',
      queryParameters: cleanQuery.isEmpty ? null : cleanQuery,
    );
  }

  String _errorMessage(String body) {
    if (body.isEmpty) {
      return 'API indisponível ou resposta vazia.';
    }
    try {
      final json = jsonDecode(body);
      if (json is Map<String, dynamic>) {
        return json['detail'] as String? ?? json['title'] as String? ?? body;
      }
    } on FormatException {
      return body;
    }
    return body;
  }
}
