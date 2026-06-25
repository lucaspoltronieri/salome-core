import 'package:flutter/material.dart';

import 'screens/home_screen.dart';
import 'screens/login_screen.dart';
import 'session/session.dart';

/// Sessão global (token + usuário). Telas usam `session.api`.
final Session session = Session();

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  session.carregar();
  runApp(const TorreApp());
}

class TorreApp extends StatelessWidget {
  const TorreApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Torre',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(colorSchemeSeed: Colors.blue, useMaterial3: true),
      home: ListenableBuilder(
        listenable: session,
        builder: (_, __) => session.logado ? const HomeScreen() : const LoginScreen(),
      ),
    );
  }
}
