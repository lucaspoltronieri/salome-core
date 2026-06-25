import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../config.dart';
import '../main.dart';
import '../widgets/dialogos.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _login = TextEditingController();
  final _senha = TextEditingController();
  bool _carregando = false;

  @override
  void dispose() {
    _login.dispose();
    _senha.dispose();
    super.dispose();
  }

  Future<void> _entrar() async {
    final login = _login.text.trim();
    final senha = _senha.text;
    if (login.isEmpty || senha.isEmpty) {
      mostrarMensagem(context, 'Informe usuário e senha.', erro: true);
      return;
    }
    setState(() => _carregando = true);
    try {
      final r = await session.api.login(login, senha);
      await session.entrar(r);
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } catch (_) {
      if (mounted) mostrarMensagem(context, 'Falha ao conectar ao servidor.', erro: true);
    } finally {
      if (mounted) setState(() => _carregando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Icon(Icons.warehouse, size: 72, color: Colors.blue),
              const SizedBox(height: 8),
              const Text('Torre de Controle',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold)),
              const SizedBox(height: 32),
              TextField(
                controller: _login,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(labelText: 'Usuário', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _senha,
                obscureText: true,
                onSubmitted: (_) => _entrar(),
                decoration: const InputDecoration(labelText: 'Senha', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 20),
              FilledButton(
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                onPressed: _carregando ? null : _entrar,
                child: _carregando
                    ? const SizedBox(
                        height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                    : const Text('Entrar', style: TextStyle(fontSize: 18)),
              ),
              const SizedBox(height: 24),
              Text('Servidor: ${Config.baseUrl}',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 12, color: Colors.grey)),
            ],
          ),
        ),
      ),
    );
  }
}
