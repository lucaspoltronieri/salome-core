import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/dialogos.dart';
import 'atividade_actions.dart';

/// Separação: lista os documentos disponíveis (NO_ARMAZEM) e separa cada um
/// escolhendo o local (box) de destino.
class SeparacaoScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const SeparacaoScreen({super.key, this.atividade});

  @override
  State<SeparacaoScreen> createState() => _SeparacaoScreenState();
}

class _SeparacaoScreenState extends State<SeparacaoScreen> {
  AtividadeResumo? _atv;
  List<DocumentoOperacional> _docs = [];
  List<LocalArmazem> _locais = [];
  bool _carregando = true;
  String? _erro;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
    _init();
  }

  Future<void> _init() async {
    try {
      _atv ??= await session.api.abrirAtividade(tipo: 'SEPARACAO');
      await _carregar();
    } on ApiException catch (e) {
      setState(() {
        _erro = e.message;
        _carregando = false;
      });
    }
  }

  Future<void> _carregar() async {
    setState(() => _carregando = true);
    final docs = await session.api.disponiveis('separar');
    final locais = _locais.isEmpty ? await session.api.locais() : _locais;
    setState(() {
      _docs = docs;
      _locais = locais;
      _carregando = false;
    });
  }

  Future<void> _separar(DocumentoOperacional d) async {
    final local = await _escolherLocal();
    if (local == null) return;
    try {
      await session.api.separar(_atv!.id, d.id!, local.id);
      if (mounted) mostrarMensagem(context, 'CT-e ${d.numeroCte ?? d.id} → ${local.nome}');
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<LocalArmazem?> _escolherLocal() {
    return showModalBottomSheet<LocalArmazem>(
      context: context,
      builder: (_) => SafeArea(
        child: ListView(
          shrinkWrap: true,
          children: [
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('Box de separação', style: TextStyle(fontWeight: FontWeight.bold)),
            ),
            for (final l in _locais)
              ListTile(
                leading: const Icon(Icons.inventory_2),
                title: Text(l.nome),
                subtitle: Text('${l.codigo} · ${l.tipo}'),
                onTap: () => Navigator.pop(context, l),
              ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Separação'),
        actions: [
          if (_atv != null)
            acoesAtividadeMenu(context, _atv!.id, aoMudar: () {
              if (mounted) Navigator.pop(context);
            }),
        ],
      ),
      body: _erro != null
          ? Center(child: Padding(padding: const EdgeInsets.all(16), child: Text(_erro!)))
          : _carregando
              ? const Center(child: CircularProgressIndicator())
              : RefreshIndicator(
                  onRefresh: _carregar,
                  child: _docs.isEmpty
                      ? ListView(children: const [
                          Padding(
                              padding: EdgeInsets.all(24),
                              child: Center(child: Text('Nada para separar.')))
                        ])
                      : ListView.builder(
                          itemCount: _docs.length,
                          itemBuilder: (_, i) {
                            final d = _docs[i];
                            return ListTile(
                              leading: const Icon(Icons.call_split, color: Colors.orange),
                              title: Text('CT-e ${d.numeroCte ?? d.id}'),
                              subtitle: Text([
                                if (d.destinatario != null) d.destinatario!,
                                if (d.cidadeDestino != null) d.cidadeDestino!,
                              ].join(' · ')),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () => _separar(d),
                            );
                          },
                        ),
                ),
    );
  }
}
