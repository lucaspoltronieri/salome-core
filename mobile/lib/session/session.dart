import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../api/api_client.dart';
import '../api/torre_api.dart';
import '../models/models.dart';

/// Estado de sessão: token + usuário logado, persistido no device. Em 401 a
/// sessão é encerrada automaticamente (o ApiClient chama [_onUnauthorized]).
class Session extends ChangeNotifier {
  final ApiClient _client = ApiClient();
  late final TorreApi api = TorreApi(_client);
  Usuario? usuario;

  Session() {
    _client.onUnauthorized = _onUnauthorized;
  }

  bool get logado => usuario != null && _client.token != null;

  Future<void> carregar() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('token');
    final usuarioJson = prefs.getString('usuario');
    if (token != null && usuarioJson != null) {
      _client.token = token;
      usuario = Usuario.fromJson(jsonDecode(usuarioJson) as Map<String, dynamic>);
    }
    notifyListeners();
  }

  Future<void> entrar(LoginResult r) async {
    _client.token = r.token;
    usuario = r.usuario;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('token', r.token);
    await prefs.setString('usuario', jsonEncode(r.usuario.toJson()));
    notifyListeners();
  }

  Future<void> sairConta() async {
    _client.token = null;
    usuario = null;
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('token');
    await prefs.remove('usuario');
    notifyListeners();
  }

  void _onUnauthorized() {
    sairConta();
  }
}
