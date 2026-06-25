import 'package:flutter/material.dart';

/// Mostra um SnackBar de erro/info.
void mostrarMensagem(BuildContext context, String msg, {bool erro = false}) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
    content: Text(msg),
    backgroundColor: erro ? Colors.red.shade700 : null,
  ));
}

/// Confirmação genérica (sim/não).
Future<bool> confirmar(BuildContext context, String titulo, String mensagem,
    {String ok = 'Confirmar'}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (_) => AlertDialog(
      title: Text(titulo),
      content: Text(mensagem),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Voltar')),
        FilledButton(onPressed: () => Navigator.pop(context, true), child: Text(ok)),
      ],
    ),
  );
  return r ?? false;
}

/// Pede um motivo (texto obrigatório). Retorna null se cancelar.
Future<String?> pedirMotivo(BuildContext context, String titulo) async {
  final ctrl = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (_) => AlertDialog(
      title: Text(titulo),
      content: TextField(
        controller: ctrl,
        autofocus: true,
        decoration: const InputDecoration(labelText: 'Motivo'),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: const Text('Voltar')),
        FilledButton(
          onPressed: () {
            final t = ctrl.text.trim();
            if (t.isNotEmpty) Navigator.pop(context, t);
          },
          child: const Text('Confirmar'),
        ),
      ],
    ),
  );
}
