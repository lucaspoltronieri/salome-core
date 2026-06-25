import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../widgets/dialogos.dart';

/// Menu de ações da atividade: entrar (participar), sair, finalizar, cancelar.
/// [aoMudar] é chamado após finalizar/cancelar/sair (ex.: voltar de tela).
Widget acoesAtividadeMenu(BuildContext context, int idAtividade, {VoidCallback? aoMudar}) {
  Future<void> agir(String v) async {
    try {
      switch (v) {
        case 'entrar':
          await session.api.entrar(idAtividade);
          if (context.mounted) mostrarMensagem(context, 'Você entrou na atividade.');
          return;
        case 'sair':
          await session.api.sair(idAtividade);
          if (context.mounted) mostrarMensagem(context, 'Você saiu da atividade.');
          aoMudar?.call();
          return;
        case 'finalizar':
          if (!await confirmar(context, 'Finalizar', 'Finalizar a atividade?')) return;
          final f = await session.api.finalizar(idAtividade);
          if (context.mounted) {
            mostrarMensagem(context, 'Finalizada (${f.totalParticipantes} pessoa(s)).');
          }
          aoMudar?.call();
          return;
        case 'cancelar':
          final motivo = await pedirMotivo(context, 'Cancelar atividade');
          if (motivo == null) return;
          await session.api.cancelar(idAtividade, motivo);
          if (context.mounted) mostrarMensagem(context, 'Atividade cancelada.');
          aoMudar?.call();
          return;
      }
    } on ApiException catch (e) {
      if (context.mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  return PopupMenuButton<String>(
    onSelected: agir,
    itemBuilder: (_) => const [
      PopupMenuItem(value: 'entrar', child: Text('Entrar (participar)')),
      PopupMenuItem(value: 'sair', child: Text('Sair')),
      PopupMenuItem(value: 'finalizar', child: Text('Finalizar')),
      PopupMenuItem(value: 'cancelar', child: Text('Cancelar')),
    ],
  );
}
