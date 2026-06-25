import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart' show MediaType;

import '../config.dart';

/// Erro de chamada à API, com status HTTP e mensagem amigável (já em pt-BR
/// quando o backend devolve `{erro|message|mensagem}`).
class ApiException implements Exception {
  final int status;
  final String message;
  ApiException(this.status, this.message);
  @override
  String toString() => message;
}

class ApiClient {
  String? token;
  void Function()? onUnauthorized;
  final http.Client _http = http.Client();

  Uri _uri(String path, [Map<String, dynamic>? query]) {
    final q = query?.map((k, v) => MapEntry(k, '$v'));
    return Uri.parse('${Config.baseUrl}$path').replace(queryParameters: q);
  }

  Map<String, String> get _headers => {
        'Content-Type': 'application/json',
        if (token != null) 'Authorization': 'Bearer $token',
      };

  Future<dynamic> get(String path, {Map<String, dynamic>? query}) async {
    return _handle(await _http.get(_uri(path, query), headers: _headers));
  }

  Future<dynamic> post(String path, {Object? body, Map<String, dynamic>? query}) async {
    final r = await _http.post(_uri(path, query),
        headers: _headers, body: body == null ? null : jsonEncode(body));
    return _handle(r);
  }

  /// POST multipart: parte JSON `dados` + arquivo `foto` opcional (ocorrências).
  Future<dynamic> postMultipart(String path,
      {required Map<String, dynamic> dados, String? fotoPath}) async {
    final req = http.MultipartRequest('POST', _uri(path));
    if (token != null) req.headers['Authorization'] = 'Bearer $token';
    req.files.add(http.MultipartFile.fromString('dados', jsonEncode(dados),
        contentType: MediaType('application', 'json')));
    if (fotoPath != null) {
      req.files.add(await http.MultipartFile.fromPath('foto', fotoPath));
    }
    final r = await http.Response.fromStream(await req.send());
    return _handle(r);
  }

  dynamic _handle(http.Response r) {
    if (r.statusCode == 401) {
      onUnauthorized?.call();
      throw ApiException(401, 'Sessão expirada. Entre novamente.');
    }
    if (r.statusCode >= 200 && r.statusCode < 300) {
      if (r.bodyBytes.isEmpty) return null;
      return jsonDecode(utf8.decode(r.bodyBytes));
    }
    String msg = 'Erro ${r.statusCode}';
    try {
      final j = jsonDecode(utf8.decode(r.bodyBytes));
      if (j is Map) {
        msg = (j['erro'] ?? j['message'] ?? j['mensagem'] ?? msg).toString();
      }
    } catch (_) {}
    throw ApiException(r.statusCode, msg);
  }
}
