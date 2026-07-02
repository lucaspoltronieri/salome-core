import 'package:flutter/material.dart';

import '../models/models.dart';
import 'cronometro.dart';

/// Faixa fixa exibida quando o usuário tem uma participação ativa em alguma
/// atividade aberta da filial — lembrete visual pra não esquecer de "Sair" antes
/// de ir pra outra atividade (o que distorceria o cronômetro de horas-homem).
class AtividadeAtivaBanner extends StatelessWidget {
  final AtividadeResumo atividade;
  final VoidCallback onTap;

  const AtividadeAtivaBanner({super.key, required this.atividade, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.amber.shade800,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Row(
            children: [
              const Icon(Icons.info_outline, color: Colors.white),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  'Você está em: ${atividade.rotuloTipo}'
                  '${atividade.placaVeiculo != null ? ' · ${atividade.placaVeiculo}' : ''}',
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
              const SizedBox(width: 8),
              Cronometro(
                inicio: Cronometro.parse(atividade.iniciadaEm),
                estilo: const TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
                icone: null,
              ),
              const SizedBox(width: 8),
              const Icon(Icons.chevron_right, color: Colors.white),
            ],
          ),
        ),
      ),
    );
  }
}
