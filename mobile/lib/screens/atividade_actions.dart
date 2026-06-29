import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../widgets/dialogos.dart';

/// Ações da atividade extraídas em funções públicas, para que tanto os **botões
/// grandes na tela** quanto o menu (3 pontinhos) reusem a mesma lógica.
/// Todas retornam `true` quando a ação foi efetivada (útil para recarregar/voltar).

Future<bool> entrarAtividade(BuildContext context, int idAtividade) async {
  try {
    await session.api.entrar(idAtividade);
    if (context.mounted) mostrarMensagem(context, 'Você entrou na atividade.');
    return true;
  } on ApiException catch (e) {
    if (context.mounted) mostrarMensagem(context, e.message, erro: true);
    return false;
  }
}

Future<bool> sairAtividade(BuildContext context, int idAtividade, {VoidCallback? aoMudar}) async {
  try {
    await session.api.sair(idAtividade);
    if (context.mounted) {
      mostrarMensagem(context, 'Você saiu (a atividade continua para os outros).');
    }
    aoMudar?.call();
    return true;
  } on ApiException catch (e) {
    if (context.mounted) mostrarMensagem(context, e.message, erro: true);
    return false;
  }
}

Future<bool> concluirAtividade(BuildContext context, int idAtividade, {VoidCallback? aoMudar}) async {
  if (!await confirmar(context, 'Concluir',
      'Concluir a atividade? Os documentos marcados viram o status final.')) {
    return false;
  }
  try {
    final c = await session.api.concluir(idAtividade);
    if (context.mounted) {
      mostrarMensagem(context, 'Concluída (${c.totalParticipantes} pessoa(s)).');
    }
    aoMudar?.call();
    return true;
  } on ApiException catch (e) {
    if (context.mounted) mostrarMensagem(context, e.message, erro: true);
    return false;
  }
}

Future<bool> finalizarAtividade(BuildContext context, int idAtividade, {VoidCallback? aoMudar}) async {
  if (!await confirmar(context, 'Finalizar', 'Finalizar a atividade?')) return false;
  try {
    final f = await session.api.finalizar(idAtividade);
    if (context.mounted) {
      mostrarMensagem(context, 'Finalizada (${f.totalParticipantes} pessoa(s)).');
    }
    aoMudar?.call();
    return true;
  } on ApiException catch (e) {
    if (context.mounted) mostrarMensagem(context, e.message, erro: true);
    return false;
  }
}

Future<bool> cancelarAtividade(BuildContext context, int idAtividade, {VoidCallback? aoMudar}) async {
  final motivo = await pedirMotivo(context, 'Cancelar atividade');
  if (motivo == null) return false;
  try {
    await session.api.cancelar(idAtividade, motivo);
    if (context.mounted) mostrarMensagem(context, 'Atividade cancelada.');
    aoMudar?.call();
    return true;
  } on ApiException catch (e) {
    if (context.mounted) mostrarMensagem(context, e.message, erro: true);
    return false;
  }
}

/// Menu de ações da atividade (3 pontinhos). [aoMudar] é chamado após
/// concluir/finalizar/cancelar/sair (ex.: voltar de tela). [usarConcluir] troca
/// "Finalizar" por "Concluir" (efetiva status final dos documentos) — descarga/carregamento.
/// [jaParticipo] esconde "Entrar" para quem já está ativo na atividade.
/// [apenasCancelar] reduz o menu só a "Cancelar" (as demais ações viram botões grandes).
Widget acoesAtividadeMenu(BuildContext context, int idAtividade,
    {VoidCallback? aoMudar,
    bool usarConcluir = false,
    bool jaParticipo = false,
    bool apenasCancelar = false}) {
  Future<void> agir(String v) async {
    switch (v) {
      case 'entrar':
        await entrarAtividade(context, idAtividade);
        return;
      case 'sair':
        await sairAtividade(context, idAtividade, aoMudar: aoMudar);
        return;
      case 'concluir':
        await concluirAtividade(context, idAtividade, aoMudar: aoMudar);
        return;
      case 'finalizar':
        await finalizarAtividade(context, idAtividade, aoMudar: aoMudar);
        return;
      case 'cancelar':
        await cancelarAtividade(context, idAtividade, aoMudar: aoMudar);
        return;
    }
  }

  return PopupMenuButton<String>(
    onSelected: agir,
    itemBuilder: (_) => [
      if (!apenasCancelar && !jaParticipo)
        const PopupMenuItem(value: 'entrar', child: Text('Entrar (participar)')),
      if (!apenasCancelar) const PopupMenuItem(value: 'sair', child: Text('Sair')),
      if (!apenasCancelar)
        if (usarConcluir)
          const PopupMenuItem(value: 'concluir', child: Text('Concluir'))
        else
          const PopupMenuItem(value: 'finalizar', child: Text('Finalizar')),
      const PopupMenuItem(value: 'cancelar', child: Text('Cancelar')),
    ],
  );
}
