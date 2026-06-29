import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/atividade_app_bar.dart';
import '../widgets/barra_atividade.dart';
import '../widgets/dialogos.dart';
import 'atividade_actions.dart';

/// Outras atividades: subtipo escolhido por BOTÃO (catálogo fixo, sem texto livre).
/// Abre a atividade, mostra o relógio rodando e as ações (sair/finalizar).
class OutrasScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const OutrasScreen({super.key, this.atividade});

  @override
  State<OutrasScreen> createState() => _OutrasScreenState();
}

/// Catálogo fixo espelhando `SubtipoOutras` no backend (codigo → rótulo/ícone).
const _subtipos = <_SubOutras>[
  _SubOutras('LANCAR_DADOS', 'Lançar dados no sistema', Icons.keyboard),
  _SubOutras('ATENDIMENTO', 'Atendimento ao cliente', Icons.support_agent),
  _SubOutras('LIMPEZA_ARMAZEM', 'Limpeza e arrumação do armazém', Icons.cleaning_services),
  _SubOutras('LIMPEZA_VEICULOS', 'Limpeza de veículos', Icons.local_car_wash),
  _SubOutras('OUTRAS', 'Outras atividade', Icons.more_horiz),
];

class _OutrasScreenState extends State<OutrasScreen> {
  AtividadeResumo? _atv;
  bool _abrindo = false;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
  }

  Future<void> _abrir(_SubOutras sub) async {
    setState(() => _abrindo = true);
    try {
      final atv = await session.api.abrirAtividade(tipo: 'OUTRAS', subtipo: sub.codigo);
      setState(() => _atv = atv);
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      if (mounted) setState(() => _abrindo = false);
    }
  }

  String _rotulo(String? codigo) {
    if (codigo == 'INTERVALO') return 'Intervalo / Almoço';
    return _subtipos.firstWhere((s) => s.codigo == codigo, orElse: () => _subtipos.last).rotulo;
  }

  Future<void> _finalizar() async {
    final atv = _atv;
    if (atv == null) return;
    await finalizarAtividade(context, atv.id, aoMudar: () {
      if (mounted) Navigator.pop(context);
    });
  }

  Future<void> _cancelar() async {
    final atv = _atv;
    if (atv == null) return;
    await cancelarAtividade(context, atv.id, aoMudar: () {
      if (mounted) Navigator.pop(context);
    });
  }

  @override
  Widget build(BuildContext context) {
    final atv = _atv;
    return PopScope(
      canPop: atv == null,
      onPopInvoked: (didPop) {
        if (!didPop && mounted) {
          mostrarMensagem(context, 'Use "Finalizar" ou "Cancelar" para fechar a atividade.');
        }
      },
      child: Scaffold(
        appBar: atv == null
            ? AppBar(title: const Text('Outras atividades'))
            : appBarAtividade(
                context,
                titulo: 'Outras atividades',
                iniciadaEm: atv.iniciadaEm,
                idAtividade: atv.id,
                mostrarCancelar: false,
              ),
        bottomNavigationBar: atv == null
            ? null
            : SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      botaoGrandeAtividade(
                        icone: Icons.check_circle,
                        texto: 'Finalizar',
                        onPressed: _finalizar,
                        cor: Colors.green,
                      ),
                      TextButton(
                          onPressed: _cancelar, child: const Text('Cancelar atividade')),
                    ],
                  ),
                ),
              ),
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: atv == null
              ? _grade()
              : Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.timer, size: 64, color: Colors.blue),
                      const SizedBox(height: 12),
                      Text('${_rotulo(atv.subtipo)} (#${atv.id})',
                          textAlign: TextAlign.center,
                          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w600)),
                      const SizedBox(height: 8),
                      const Text('Atividade em andamento — o tempo corre no topo.',
                          style: TextStyle(color: Colors.grey)),
                    ],
                  ),
                ),
        ),
      ),
    );
  }

  Widget _grade() {
    if (_abrindo) return const Center(child: CircularProgressIndicator());
    return GridView.count(
      crossAxisCount: 2,
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      childAspectRatio: 1.3,
      children: _subtipos
          .map((s) => Card(
                child: InkWell(
                  onTap: () => _abrir(s),
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(s.icone, size: 36, color: Colors.blue),
                      const SizedBox(height: 8),
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        child: Text(s.rotulo, textAlign: TextAlign.center),
                      ),
                    ],
                  ),
                ),
              ))
          .toList(),
    );
  }
}

class _SubOutras {
  final String codigo;
  final String rotulo;
  final IconData icone;
  const _SubOutras(this.codigo, this.rotulo, this.icone);
}
