import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../chave/chave_parser.dart';
import '../../main.dart';
import '../../models/box_destino.dart';
import '../../models/models.dart';
import '../../widgets/atividade_app_bar.dart';
import '../../widgets/barra_atividade.dart';
import '../../widgets/dialogos.dart';
import '../../widgets/scanner_sheet.dart';
import '../../widgets/seletor_placa.dart';
import '../atividade_actions.dart';

/// Descarga de coleta: bipa a NF (chave de 44 dígitos) pela câmera ou digitação e
/// escolhe o destino — Box Transferência, Box Distribuição ou Crossdock direto (a NF
/// entra direto no caminhão de transferência). Guarda só a chave; o CT-e é casado depois.
class ColetaScreen extends StatefulWidget {
  final AtividadeResumo atividade;
  const ColetaScreen({super.key, required this.atividade});

  @override
  State<ColetaScreen> createState() => _ColetaScreenState();
}

class _ColetaScreenState extends State<ColetaScreen> {
  late AtividadeResumo _atv;
  List<DocumentoOperacional> _docs = [];
  List<LocalArmazem> _locais = [];
  bool _carregando = true;
  String _filtro = '';

  int get _idAtividade => _atv.id;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
    _carregar();
  }

  Future<void> _participar() async {
    final ok = await entrarAtividade(context, _idAtividade);
    if (ok) {
      final a = await session.api.buscarAtividade(_idAtividade);
      if (mounted) setState(() => _atv = a);
    }
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

  LocalArmazem? _box(String codigo) {
    for (final l in _locais) {
      if (l.ativo && l.codigo.toUpperCase() == codigo) return l;
    }
    return null;
  }

  /// Folha de destino própria da coleta: Transferência, Distribuição ou Crossdock.
  Future<String?> _escolherDestino() {
    return showModalBottomSheet<String>(
      context: context,
      builder: (_) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('Destino da NF',
                  style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            ),
            _botaoDestino('Box Transferência', Colors.blue.shade700, 'TRANSF'),
            _botaoDestino('Box Distribuição', Colors.green.shade700, 'DIST'),
            _botaoDestino('Crossdock direto (caminhão transferência)',
                Colors.purple.shade700, 'CROSSDOCK'),
            const SizedBox(height: 8),
            TextButton(
                onPressed: () => Navigator.pop(context), child: const Text('Cancelar')),
          ],
        ),
      ),
    );
  }

  Widget _botaoDestino(String texto, Color cor, String valor) => Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
        child: FilledButton(
          style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(56), backgroundColor: cor),
          onPressed: () => Navigator.pop(context, valor),
          child: Text(texto, style: const TextStyle(fontSize: 16)),
        ),
      );

  Future<void> _registrarChave(String? bruto) async {
    final chave = ChaveParser.normalizar(bruto);
    if (chave == null) {
      mostrarMensagem(context, 'Chave inválida (esperado 44 dígitos da NF).', erro: true);
      return;
    }
    if (!ChaveParser.isNfe(chave)) {
      mostrarMensagem(context, 'Bipe a chave da NF-e na coleta (modelo 55).', erro: true);
      return;
    }
    final destino = await _escolherDestino();
    if (destino == null) return;
    final numeroNf = ChaveParser.numero(chave)?.toString();
    final serie = ChaveParser.serie(chave);
    final cnpj = ChaveParser.cnpjEmitente(chave);
    try {
      if (destino == 'CROSSDOCK') {
        await _registrarCrossdock(chave, numeroNf, serie, cnpj);
        return;
      }
      final box = _box(destino == 'TRANSF' ? BoxDestino.transf : BoxDestino.dist);
      if (box == null) {
        if (mounted) {
          mostrarMensagem(context,
              'Box destino não cadastrado ou inativo para esta filial.', erro: true);
        }
        return;
      }
      await session.api.biparNf(
        _idAtividade,
        chaveNf: chave,
        numeroNf: numeroNf,
        serie: serie,
        cnpjEmitente: cnpj,
        idLocalDestino: box.id,
      );
      if (mounted) mostrarMensagem(context, 'NF registrada → ${box.nome}');
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  /// Crossdock: usa um carregamento de transferência aberto ou abre um na hora.
  Future<void> _registrarCrossdock(
      String chave, String? numeroNf, String? serie, String? cnpj) async {
    int idCarregamento;
    final abertos = await session.api.carregamentoTransferenciaAberto();
    if (abertos.isNotEmpty) {
      idCarregamento = abertos.first.id;
    } else {
      if (!mounted) return;
      final placa = await escolherPlaca(context, 'TRANSFERENCIA',
          titulo: 'Caminhão de transferência (crossdock)');
      if (placa == null) return;
      final atv = await session.api
          .abrirAtividade(tipo: 'CARREGAMENTO', subtipo: 'TRANSFERENCIA', placa: placa);
      idCarregamento = atv.id;
    }
    await session.api.biparNfCrossdock(
      _idAtividade,
      chaveNf: chave,
      numeroNf: numeroNf,
      serie: serie,
      cnpjEmitente: cnpj,
      idAtividadeCarregamento: idCarregamento,
    );
    if (mounted) mostrarMensagem(context, 'NF em crossdock direto → carregamento de transferência.');
    await _carregar();
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
          FilledButton(onPressed: () => Navigator.pop(context, ctrl.text), child: const Text('OK')),
        ],
      ),
    );
    if (chave != null) await _registrarChave(chave);
  }

  Future<void> _concluir() async {
    await finalizarAtividade(context, _idAtividade, aoMudar: () {
      if (mounted) Navigator.pop(context);
    });
  }

  List<DocumentoOperacional> get _filtrados {
    final f = _filtro.toLowerCase();
    if (f.isEmpty) return _docs;
    return _docs.where((d) {
      return (d.chaveNf ?? '').toLowerCase().contains(f) ||
          (d.numeroCte?.toString() ?? '').contains(f);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final souParticipante = _atv.souParticipanteAtivo(session.usuario?.id);
    final docs = _filtrados;
    return PopScope(
      canPop: !souParticipante,
      onPopInvoked: (didPop) {
        if (!didPop && mounted) {
          mostrarMensagem(context, 'Use "Sair" ou "Concluir" para fechar a atividade.');
        }
      },
      child: Scaffold(
        appBar: appBarAtividade(
          context,
          titulo: 'Coleta · ${_atv.placaVeiculo ?? '#$_idAtividade'}',
          iniciadaEm: _atv.iniciadaEm,
          idAtividade: _idAtividade,
          aoMudar: () {
            if (mounted) Navigator.pop(context);
          },
          acoesExtras: [
            IconButton(
                icon: const Icon(Icons.keyboard), onPressed: _digitar, tooltip: 'Digitar chave'),
          ],
        ),
        bottomNavigationBar: barraAtividadeCompartilhada(
          context,
          idAtividade: _idAtividade,
          ativos: _atv.participantesAtivos,
          souParticipante: souParticipante,
          onParticipar: _participar,
          onSair: () => sairAtividade(context, _idAtividade, aoMudar: () {
            if (mounted) Navigator.pop(context);
          }),
          primaria: souParticipante
              ? botaoGrandeAtividade(
                  icone: Icons.check_circle,
                  texto: 'Concluir descarga',
                  onPressed: _concluir,
                  cor: Colors.green,
                )
              : null,
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
                    child: Column(
                      children: [
                        Align(
                          alignment: Alignment.centerLeft,
                          child: Text('${_docs.length} NF(s) bipada(s)',
                              style: const TextStyle(fontWeight: FontWeight.bold)),
                        ),
                        const SizedBox(height: 8),
                        TextField(
                          decoration: const InputDecoration(
                            prefixIcon: Icon(Icons.search),
                            hintText: 'Filtrar por chave/NF...',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          onChanged: (v) => setState(() => _filtro = v),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: RefreshIndicator(
                      onRefresh: _carregar,
                      child: docs.isEmpty
                          ? ListView(children: const [
                              Padding(
                                  padding: EdgeInsets.all(24),
                                  child: Center(child: Text('Bipe a primeira NF.')))
                            ])
                          : ListView.builder(
                              itemCount: docs.length,
                              itemBuilder: (_, i) {
                                final d = docs[i];
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
      ),
    );
  }
}
