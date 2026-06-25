import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../chave/chave_parser.dart';
import '../../main.dart';
import '../../models/models.dart';
import '../../widgets/destino_picker.dart';
import '../../widgets/dialogos.dart';
import '../../widgets/scanner_sheet.dart';
import '../atividade_actions.dart';

/// Descarga de coleta: bipa a NF (chave de 44 dígitos) pela câmera ou digitação,
/// escolhe o destino (Transferência ou Separação) e registra o pré-CTe.
class ColetaScreen extends StatefulWidget {
  final AtividadeResumo atividade;
  const ColetaScreen({super.key, required this.atividade});

  @override
  State<ColetaScreen> createState() => _ColetaScreenState();
}

class _ColetaScreenState extends State<ColetaScreen> {
  List<DocumentoOperacional> _docs = [];
  List<LocalArmazem> _locais = [];
  bool _carregando = true;

  int get _idAtividade => widget.atividade.id;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    setState(() => _carregando = true);
    try {
      final docs = await session.api.documentosDaAtividade(_idAtividade);
      final locais = _locais.isEmpty ? await session.api.locais() : _locais;
      setState(() {
        _docs = docs;
        _locais = locais;
        _carregando = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _carregando = false);
        mostrarMensagem(context, e.message, erro: true);
      }
    }
  }

  Future<void> _registrarChave(String? bruto) async {
    final chave = ChaveParser.normalizar(bruto);
    if (chave == null) {
      mostrarMensagem(context, 'Chave inválida (esperado 44 dígitos da NF).', erro: true);
      return;
    }
    final box = await escolherDestino(context, _locais, OrigemDescarga.coleta,
        titulo: 'Destino da NF');
    if (box == null) return;
    try {
      await session.api.biparNf(
        _idAtividade,
        chaveNf: chave,
        numeroNf: ChaveParser.numero(chave)?.toString(),
        serie: ChaveParser.serie(chave),
        cnpjEmitente: ChaveParser.cnpjEmitente(chave),
        idLocalDestino: box.id,
      );
      if (mounted) mostrarMensagem(context, 'NF registrada → ${box.nome}');
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _biparCamera() async {
    final raw = await abrirScanner(context, titulo: 'Bipar NF');
    if (raw != null) await _registrarChave(raw);
  }

  Future<void> _digitar() async {
    final ctrl = TextEditingController();
    final chave = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        title: const Text('Digitar chave da NF'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: TextInputType.number,
          decoration: const InputDecoration(labelText: 'Chave (44 dígitos)'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('Voltar')),
          FilledButton(
              onPressed: () => Navigator.pop(context, ctrl.text), child: const Text('OK')),
        ],
      ),
    );
    if (chave != null) await _registrarChave(chave);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Coleta · ${widget.atividade.placaVeiculo ?? '#$_idAtividade'}'),
        actions: [
          IconButton(icon: const Icon(Icons.keyboard), onPressed: _digitar, tooltip: 'Digitar chave'),
          acoesAtividadeMenu(context, _idAtividade, aoMudar: () {
            if (mounted) Navigator.pop(context);
          }),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _biparCamera,
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('Bipar NF'),
      ),
      body: _carregando
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Text('${_docs.length} NF(s) bipada(s)',
                      style: const TextStyle(fontWeight: FontWeight.bold)),
                ),
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: _carregar,
                    child: _docs.isEmpty
                        ? ListView(children: const [
                            Padding(
                                padding: EdgeInsets.all(24),
                                child: Center(child: Text('Bipe a primeira NF.')))
                          ])
                        : ListView.builder(
                            itemCount: _docs.length,
                            itemBuilder: (_, i) {
                              final d = _docs[i];
                              return ListTile(
                                leading: const Icon(Icons.description, color: Colors.teal),
                                title: Text(d.chaveNf ?? 'NF ${d.id}',
                                    style: const TextStyle(fontSize: 13)),
                                subtitle: Text('${d.status}'
                                    '${d.volumes != null ? ' · ${d.volumes} vol' : ''}'),
                              );
                            },
                          ),
                  ),
                ),
              ],
            ),
    );
  }
}
