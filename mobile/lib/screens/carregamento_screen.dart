import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../chave/chave_parser.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/atividade_app_bar.dart';
import '../widgets/barra_atividade.dart';
import '../widgets/dialogos.dart';
import '../widgets/scanner_sheet.dart';
import '../widgets/seletor_placa.dart';
import 'atividade_actions.dart';

/// Carregamento: escolhe Entrega ou Transferência, depois a placa do caminhão e
/// marca os CT-es por box (ficam EM_CARREGAMENTO; só ao **Concluir** viram CARREGADO).
/// Entrega tem Box Distribuição (vem marcado), Box Separação e CT-es ainda no caminhão
/// (crossdock direto). Transferência mostra o Box Transferência. Bipar marca na hora.
class CarregamentoScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const CarregamentoScreen({super.key, this.atividade});

  @override
  State<CarregamentoScreen> createState() => _CarregamentoScreenState();
}

class _CarregamentoScreenState extends State<CarregamentoScreen> {
  AtividadeResumo? _atv;
  String? _tipo; // ENTREGA | TRANSFERENCIA
  List<DocumentoOperacional> _carregaveis = [];
  List<DocumentoOperacional> _marcados = []; // EM_CARREGAMENTO nesta atividade
  final Set<int> _selecionados = {};
  bool _carregando = false;
  bool _ocupado = false;
  bool _preSelecionado = false;
  String _filtro = '';
  String? _erro;

  bool get _transferencia => _tipo == 'TRANSFERENCIA';

  @override
  void initState() {
    super.initState();
    if (widget.atividade != null) {
      _atv = widget.atividade;
      _tipo = widget.atividade!.subtipo ?? 'ENTREGA';
      _carregar();
    }
  }

  // ---- Passo 1/2: escolher tipo + placa e abrir a atividade ----------
  Future<void> _iniciar(String tipo) async {
    final placa = await escolherPlaca(context, tipo,
        titulo: tipo == 'ENTREGA' ? 'Caminhão de Entrega' : 'Caminhão de Transferência');
    if (placa == null) return;
    try {
      final atv = await session.api
          .abrirAtividade(tipo: 'CARREGAMENTO', subtipo: tipo, placa: placa);
      if (!mounted) return;
      setState(() {
        _atv = atv;
        _tipo = tipo;
      });
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _carregar() async {
    setState(() => _carregando = true);
    try {
      final docs = await session.api.carregaveis(_tipo!);
      final marcados = (await session.api.documentosDaAtividade(_atv!.id))
          .where((d) => d.status == 'EM_CARREGAMENTO')
          .toList();
      setState(() {
        _carregaveis = docs;
        _marcados = marcados;
        // Entrega: Box Distribuição já vem marcado por padrão (crossdock direto padrão).
        if (!_preSelecionado && !_transferencia) {
          _selecionados.addAll(
              docs.where((d) => d.status == 'SEPARADO_BOX').map((d) => d.id!).whereType<int>());
          _preSelecionado = true;
        }
        _erro = null;
        _carregando = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _erro = e.message;
          _carregando = false;
        });
      }
    }
  }

  Future<void> _participar() async {
    if (_atv == null) return;
    final ok = await entrarAtividade(context, _atv!.id);
    if (ok) {
      final a = await session.api.buscarAtividade(_atv!.id);
      if (mounted) setState(() => _atv = a);
    }
  }

  Future<void> _carregarSelecionados() async {
    if (_selecionados.isEmpty) return;
    setState(() => _ocupado = true);
    try {
      await session.api.carregarLote(_atv!.id, _selecionados.toList());
      if (mounted) mostrarMensagem(context, '${_selecionados.length} CT-e(s) em carregamento.');
      setState(() => _selecionados.clear());
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _concluir() async {
    if (!await confirmar(context, 'Concluir carregamento',
        'Concluir? Os CT-es marcados viram CARREGADO.')) {
      return;
    }
    try {
      final f = await session.api.concluir(_atv!.id);
      if (mounted) {
        mostrarMensagem(context, 'Carregamento concluído (${f.totalParticipantes} pessoa(s)).');
        Navigator.pop(context);
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _bipar() async {
    final raw = await abrirScanner(context);
    if (raw == null) return;
    final chave = ChaveParser.normalizar(raw);
    final numero = chave != null ? ChaveParser.numero(chave) : int.tryParse(raw.trim());
    if (_marcados.any((d) => d.numeroCte == numero)) {
      if (mounted) mostrarMensagem(context, 'CT-e $numero já está em carregamento.');
      return;
    }
    DocumentoOperacional? achado;
    for (final d in _carregaveis) {
      if (d.numeroCte != null && d.numeroCte == numero) {
        achado = d;
        break;
      }
    }
    if (achado == null || achado.id == null) {
      if (mounted) {
        mostrarMensagem(context, 'CT-e $numero não está disponível para este carregamento.', erro: true);
      }
      return;
    }
    setState(() => _selecionados.add(achado!.id!));
    if (mounted) mostrarMensagem(context, 'CT-e $numero marcado para carregar.');
  }

  void _alternar(int? id) {
    if (id == null) return;
    setState(() {
      if (_selecionados.contains(id)) {
        _selecionados.remove(id);
      } else {
        _selecionados.add(id);
      }
    });
  }

  List<DocumentoOperacional> _filtrar(Iterable<DocumentoOperacional> docs) {
    final f = _filtro.toLowerCase();
    final lista = docs.toList();
    if (f.isEmpty) return lista;
    return lista.where((d) {
      return (d.numeroCte?.toString() ?? '').contains(f) ||
          (d.destinatario ?? '').toLowerCase().contains(f) ||
          (d.cidadeDestino ?? '').toLowerCase().contains(f);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    if (_atv == null) return _telaEscolha();
    final souParticipante = _atv!.souParticipanteAtivo(session.usuario?.id);
    final titulo = 'Carregamento · ${_transferencia ? 'Transf' : 'Entrega'}'
        '${_atv!.placaVeiculo != null ? ' · ${_atv!.placaVeiculo}' : ''}';
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
          titulo: titulo,
          iniciadaEm: _atv!.iniciadaEm,
          idAtividade: _atv!.id,
          aoMudar: () {
            if (mounted) Navigator.pop(context);
          },
        ),
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _bipar,
          icon: const Icon(Icons.qr_code_scanner),
          label: const Text('Bipar'),
        ),
        bottomNavigationBar: barraAtividadeCompartilhada(
          context,
          idAtividade: _atv!.id,
          ativos: _atv!.participantesAtivos,
          souParticipante: souParticipante,
          onParticipar: _participar,
          onSair: () => sairAtividade(context, _atv!.id, aoMudar: () {
            if (mounted) Navigator.pop(context);
          }),
          primaria: _primaria(souParticipante),
        ),
        body: _erro != null
            ? Center(child: Padding(padding: const EdgeInsets.all(16), child: Text(_erro!)))
            : _carregando
                ? const Center(child: CircularProgressIndicator())
                : Column(
                    children: [
                      Padding(
                        padding: const EdgeInsets.all(12),
                        child: TextField(
                          decoration: const InputDecoration(
                            prefixIcon: Icon(Icons.search),
                            hintText: 'Filtrar por CT-e, destino...',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          onChanged: (v) => setState(() => _filtro = v),
                        ),
                      ),
                      Expanded(
                        child: RefreshIndicator(
                          onRefresh: _carregar,
                          child: ListView(children: _secoes()),
                        ),
                      ),
                    ],
                  ),
      ),
    );
  }

  Widget? _primaria(bool souParticipante) {
    if (!souParticipante) return null;
    if (_selecionados.isNotEmpty) {
      return botaoGrandeAtividade(
        icone: Icons.playlist_add_check,
        texto: 'Carregar ${_selecionados.length} selecionado(s)',
        onPressed: _ocupado ? null : _carregarSelecionados,
      );
    }
    if (_marcados.isNotEmpty) {
      return botaoGrandeAtividade(
        icone: Icons.check_circle,
        texto: 'Concluir carregamento',
        onPressed: _concluir,
        cor: Colors.green,
      );
    }
    return null;
  }

  List<Widget> _secoes() {
    final widgets = <Widget>[];
    if (_transferencia) {
      widgets.addAll(_secao('Box Transferência', Icons.swap_horiz, Colors.blue,
          _filtrar(_carregaveis)));
    } else {
      widgets.addAll(_secao('Box Distribuição', Icons.inventory_2, Colors.green,
          _filtrar(_carregaveis.where((d) => d.status == 'SEPARADO_BOX'))));
      widgets.addAll(_secao('Box Separação (sem separar)', Icons.call_split, Colors.orange,
          _filtrar(_carregaveis.where((d) => d.status == 'NO_ARMAZEM'))));
      widgets.addAll(_secao('No caminhão (crossdock direto)', Icons.local_shipping, Colors.purple,
          _filtrar(_carregaveis.where((d) => d.status == 'EM_DESCARGA'))));
    }
    if (_marcados.isNotEmpty) widgets.add(_secaoMarcados());
    if (widgets.isEmpty) {
      widgets.add(const Padding(
          padding: EdgeInsets.all(24), child: Center(child: Text('Nada para carregar.'))));
    }
    return widgets;
  }

  List<Widget> _secao(String titulo, IconData icone, Color cor, List<DocumentoOperacional> docs) {
    if (docs.isEmpty) return const [];
    return [
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
        child: Row(children: [
          Icon(icone, size: 18, color: cor),
          const SizedBox(width: 8),
          Text('$titulo (${docs.length})',
              style: TextStyle(fontWeight: FontWeight.bold, color: cor)),
        ]),
      ),
      ...docs.map(_linha),
    ];
  }

  Widget _linha(DocumentoOperacional d) {
    final sel = _selecionados.contains(d.id);
    return ListTile(
      leading: Checkbox(value: sel, onChanged: (_) => _alternar(d.id)),
      title: Text('CT-e ${d.numeroCte ?? d.id}'),
      subtitle: Text([
        if (d.destinatario != null) d.destinatario!,
        if (d.cidadeDestino != null) d.cidadeDestino!,
      ].join(' · ')),
      onTap: () => _alternar(d.id),
    );
  }

  Widget _secaoMarcados() {
    return ExpansionTile(
      leading: const Icon(Icons.local_shipping, color: Colors.orange),
      title: Text('Em carregamento (${_marcados.length})'),
      children: _marcados
          .map((d) => ListTile(
                dense: true,
                leading: const Icon(Icons.check_circle, color: Colors.green),
                title: Text('CT-e ${d.numeroCte ?? d.id}'),
                subtitle: Text(d.destinatario ?? d.cidadeDestino ?? ''),
              ))
          .toList(),
    );
  }

  Widget _telaEscolha() {
    return Scaffold(
      appBar: AppBar(title: const Text('Carregamento')),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text('O que vai carregar?',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 24),
            botaoGrandeAtividade(
              icone: Icons.inventory_2,
              texto: 'Entrega',
              onPressed: () => _iniciar('ENTREGA'),
              cor: Colors.green,
            ),
            const SizedBox(height: 16),
            botaoGrandeAtividade(
              icone: Icons.swap_horiz,
              texto: 'Transferência',
              onPressed: () => _iniciar('TRANSFERENCIA'),
              cor: Colors.blue,
            ),
          ],
        ),
      ),
    );
  }
}
