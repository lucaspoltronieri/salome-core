import 'package:flutter/material.dart';

import '../models/box_destino.dart';
import '../models/models.dart';

/// Pergunta o box destino (filtrado pela origem). Retorna o local escolhido,
/// ou null se cancelar.
Future<LocalArmazem?> escolherDestino(
  BuildContext context,
  List<LocalArmazem> locais,
  OrigemDescarga origem, {
  String? titulo,
}) {
  final boxes = BoxDestino.boxesPara(locais, origem);
  return showModalBottomSheet<LocalArmazem>(
    context: context,
    builder: (_) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: Text(titulo ?? 'Destino do documento',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
          ),
          if (boxes.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('Nenhum box destino cadastrado para esta filial. '
                  'Cadastre os boxes (Separação/Distribuição/Transferência) no admin.'),
            ),
          for (final box in boxes)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
              child: FilledButton(
                style: FilledButton.styleFrom(
                    minimumSize: const Size.fromHeight(56),
                    backgroundColor: _cor(box.codigo)),
                onPressed: () => Navigator.of(context).pop(box),
                child: Text(BoxDestino.rotulo(box), style: const TextStyle(fontSize: 18)),
              ),
            ),
          const SizedBox(height: 8),
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Cancelar'),
          ),
        ],
      ),
    ),
  );
}

Color _cor(String codigo) {
  switch (codigo.toUpperCase()) {
    case BoxDestino.sep:
      return Colors.orange.shade700;
    case BoxDestino.dist:
      return Colors.green.shade700;
    case BoxDestino.transf:
      return Colors.blue.shade700;
    default:
      return Colors.grey.shade700;
  }
}
