import 'dart:async';

import 'package:flutter/material.dart';

/// Mostra o tempo decorrido desde [inicio] (UTC/ISO do servidor), atualizando a
/// cada segundo. Usado para o relógio da atividade/participação rodando no app.
class Cronometro extends StatefulWidget {
  final DateTime? inicio;
  final TextStyle? estilo;
  final IconData? icone;

  const Cronometro({super.key, required this.inicio, this.estilo, this.icone = Icons.timer_outlined});

  /// Converte o ISO do backend (`iniciadaEm`, `entradaEm`) em DateTime local.
  static DateTime? parse(String? iso) {
    if (iso == null || iso.isEmpty) return null;
    return DateTime.tryParse(iso)?.toLocal();
  }

  @override
  State<Cronometro> createState() => _CronometroState();
}

class _CronometroState extends State<Cronometro> {
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  String _fmt(Duration d) {
    final h = d.inHours;
    final m = d.inMinutes % 60;
    final s = d.inSeconds % 60;
    String dois(int n) => n.toString().padLeft(2, '0');
    return h > 0 ? '${dois(h)}:${dois(m)}:${dois(s)}' : '${dois(m)}:${dois(s)}';
  }

  @override
  Widget build(BuildContext context) {
    final inicio = widget.inicio;
    final texto = inicio == null ? '--:--' : _fmt(DateTime.now().difference(inicio));
    final estilo = widget.estilo ??
        const TextStyle(fontFeatures: [FontFeature.tabularFigures()], fontWeight: FontWeight.w600);
    if (widget.icone == null) {
      return Text(texto, style: estilo);
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(widget.icone, size: 16, color: estilo.color),
        const SizedBox(width: 4),
        Text(texto, style: estilo),
      ],
    );
  }
}
