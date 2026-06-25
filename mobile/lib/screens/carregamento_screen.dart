import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/dialogos.dart';
import 'atividade_actions.dart';

/// Carregamento: lista os documentos prontos (NO_ARMAZEM ou SEPARADO_BOX) e
/// carrega cada um (cross-dock direto quando ainda não passou por separação).
class CarregamentoScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const CarregamentoScreen({super.key, this.atividade});

  @override
  State<CarregamentoScreen> createState() => _CarregamentoScreenState();
}

class _CarregamentoScreenState extends State<CarregamentoScreen> {
  AtividadeResumo? _atv;
  List<DocumentoOperacional> _docs = [];
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
      _atv ??= await session.api.abrirAtividade(tipo: 'CARREGAMENTO');
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
    final docs = await session.api.disponiveis('carregar');
    setState(() {
      _docs = docs;
      _carregando = false;
    });
  }

  Future<void> _carregarDoc(DocumentoOperacional d) async {
    if (!await confirmar(context, 'Carregar', 'Carregar o CT-e ${d.numeroCte ?? d.id}?')) return;
    try {
      await session.api.carregar(_atv!.id, d.id!);
      if (mounted) mostrarMensagem(context, 'CT-e ${d.numeroCte ?? d.id} carregado.');
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Carregamento'),
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
                              child: Center(child: Text('Nada para carregar.')))
                        ])
                      : ListView.builder(
                          itemCount: _docs.length,
                          itemBuilder: (_, i) {
                            final d = _docs[i];
                            final cross = d.status == 'NO_ARMAZEM';
                            return ListTile(
                              leading: Icon(Icons.upload,
                                  color: cross ? Colors.purple : Colors.green),
                              title: Text('CT-e ${d.numeroCte ?? d.id}'),
                              subtitle: Text([
                                if (cross) 'cross-dock direto',
                                if (d.destinatario != null) d.destinatario!,
                                if (d.cidadeDestino != null) d.cidadeDestino!,
                              ].join(' · ')),
                              trailing: const Icon(Icons.chevron_right),
                              onTap: () => _carregarDoc(d),
                            );
                          },
                        ),
                ),
    );
  }
}
