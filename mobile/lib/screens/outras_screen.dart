import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/dialogos.dart';
import 'atividade_actions.dart';

/// Outras atividades: exige um subtipo. Abre a atividade e mostra as ações
/// (entrar/sair/finalizar/cancelar).
class OutrasScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const OutrasScreen({super.key, this.atividade});

  @override
  State<OutrasScreen> createState() => _OutrasScreenState();
}

class _OutrasScreenState extends State<OutrasScreen> {
  final _subtipo = TextEditingController();
  AtividadeResumo? _atv;
  bool _abrindo = false;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
  }

  @override
  void dispose() {
    _subtipo.dispose();
    super.dispose();
  }

  Future<void> _abrir() async {
    final sub = _subtipo.text.trim();
    if (sub.isEmpty) {
      mostrarMensagem(context, 'Informe o tipo da atividade (subtipo).', erro: true);
      return;
    }
    setState(() => _abrindo = true);
    try {
      final atv = await session.api.abrirAtividade(tipo: 'OUTRAS', subtipo: sub);
      setState(() => _atv = atv);
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      if (mounted) setState(() => _abrindo = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final atv = _atv;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Outras atividades'),
        actions: [
          if (atv != null)
            acoesAtividadeMenu(context, atv.id, aoMudar: () {
              if (mounted) Navigator.pop(context);
            }),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: atv == null
            ? Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  TextField(
                    controller: _subtipo,
                    decoration: const InputDecoration(
                      labelText: 'Tipo da atividade (ex.: Inventário, Limpeza)',
                      border: OutlineInputBorder(),
                    ),
                  ),
                  const SizedBox(height: 16),
                  FilledButton(
                    style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                    onPressed: _abrindo ? null : _abrir,
                    child: _abrindo
                        ? const SizedBox(
                            height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Iniciar atividade'),
                  ),
                ],
              )
            : Center(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.timer, size: 64, color: Colors.blue),
                    const SizedBox(height: 12),
                    Text('Atividade "${atv.subtipo ?? ''}" em andamento (#${atv.id}).',
                        textAlign: TextAlign.center),
                    const SizedBox(height: 8),
                    const Text('Use o menu (⋮) para finalizar quando terminar.',
                        style: TextStyle(color: Colors.grey)),
                  ],
                ),
              ),
      ),
    );
  }
}
