import 'package:flutter/material.dart';

import '../screens/atividade_actions.dart';
import 'cronometro.dart';

/// AppBar padrão das telas de atividade:
/// - **sem seta de voltar** (a saída é por Sair/Finalizar/Concluir — botões grandes);
/// - **cronômetro grande** abaixo do título, para o operador ver o tempo de relance;
/// - menu (3 pontinhos) reduzido a **Cancelar** (ações secundárias).
///
/// [acoesExtras] entram antes do menu (ex.: toggle "selecionar vários", teclado da coleta).
PreferredSizeWidget appBarAtividade(
  BuildContext context, {
  required String titulo,
  String? iniciadaEm,
  required int idAtividade,
  VoidCallback? aoMudar,
  List<Widget> acoesExtras = const [],
  bool mostrarCancelar = true,
}) {
  return AppBar(
    automaticallyImplyLeading: false,
    title: Text(titulo),
    actions: [
      ...acoesExtras,
      if (mostrarCancelar)
        acoesAtividadeMenu(context, idAtividade, apenasCancelar: true, aoMudar: aoMudar),
    ],
    bottom: PreferredSize(
      preferredSize: const Size.fromHeight(44),
      child: Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Cronometro(
          inicio: Cronometro.parse(iniciadaEm),
          icone: Icons.timer_outlined,
          estilo: const TextStyle(
            color: Colors.white,
            fontSize: 26,
            fontWeight: FontWeight.bold,
            fontFeatures: [FontFeature.tabularFigures()],
          ),
        ),
      ),
    ),
  );
}
