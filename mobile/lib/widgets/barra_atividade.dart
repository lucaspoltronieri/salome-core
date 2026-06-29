import 'package:flutter/material.dart';

import 'pessoas_atividade.dart';

/// Botão grande padrão (largura total, alto pra tocar com luva).
Widget botaoGrandeAtividade({
  required IconData icone,
  required String texto,
  required VoidCallback? onPressed,
  Color? cor,
}) {
  return FilledButton.icon(
    icon: Icon(icone),
    label: Text(texto),
    onPressed: onPressed,
    style: FilledButton.styleFrom(
      minimumSize: const Size.fromHeight(52),
      backgroundColor: cor,
    ),
  );
}

/// Barra inferior das telas COMPARTILHADAS (descarga/carregamento/coleta).
///
/// - `somentePrimaria`: mostra só a ação primária (ex.: durante "selecionar vários").
/// - se **não** sou participante: mostra um botão grande **"Participar desta atividade"**.
/// - se sou participante: ação primária (quando houver) + linha **Pessoas · Sair**.
Widget barraAtividadeCompartilhada(
  BuildContext context, {
  required int idAtividade,
  required int ativos,
  required bool souParticipante,
  required VoidCallback onParticipar,
  required VoidCallback onSair,
  Widget? primaria,
  bool somentePrimaria = false,
}) {
  Widget conteudo;
  if (somentePrimaria) {
    conteudo = primaria ?? const SizedBox.shrink();
  } else if (!souParticipante) {
    conteudo = botaoGrandeAtividade(
      icone: Icons.login,
      texto: 'Participar desta atividade',
      onPressed: onParticipar,
      cor: Colors.green,
    );
  } else {
    conteudo = Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (primaria != null) ...[primaria, const SizedBox(height: 8)],
        Row(
          children: [
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.groups),
                label: Text('Pessoas ($ativos)'),
                onPressed: () => mostrarPessoas(context, idAtividade),
                style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.logout),
                label: const Text('Sair'),
                onPressed: onSair,
                style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
              ),
            ),
          ],
        ),
      ],
    );
  }
  return SafeArea(
    child: Padding(padding: const EdgeInsets.all(12), child: conteudo),
  );
}
