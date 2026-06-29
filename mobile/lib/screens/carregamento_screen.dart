import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/atividade_app_bar.dart';
import '../widgets/barra_atividade.dart';
import '../widgets/dialogos.dart';
import 'atividade_actions.dart';

/// Carregamento: marca os documentos que vão no caminhão (ficam EM_CARREGAMENTO) e
/// só ao **Concluir** viram CARREGADO. Modo rápido (selecionar vários) e detalhado.
class CarregamentoScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const CarregamentoScreen({super.key, this.atividade});

  @override
  State<CarregamentoScreen> createState() => _CarregamentoScreenState();
}

class _CarregamentoScreenState extends State<CarregamentoScreen> {
  AtividadeResumo? _atv;
  List<DocumentoOperacional> _disponiveis = [];
  List<DocumentoOperacional> _marcados = []; // EM_CARREGAMENTO nesta atividade
  final Set<int> _selecionados = {};
  bool _modoSelecao = false;
  bool _carregando = true;
  bool _ocupado = false;
  String? _erro;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
    _init();
  }

  Future<void> _init() async {
    try {
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
    final marcados = _atv == null
        ? <DocumentoOperacional>[]
        : await session.api.documentosDaAtividade(_atv!.id);
    setState(() {
      _disponiveis = docs;
      _marcados = marcados;
      _carregando = false;
    });
  }

  Future<int> _garantirAtividade() async {
    _atv ??= await session.api.abrirAtividade(tipo: 'CARREGAMENTO');
    return _atv!.id;
  }

  Future<void> _marcarUm(DocumentoOperacional d) async {
    if (!await confirmar(context, 'Carregar', 'Marcar o CT-e ${d.numeroCte ?? d.id} para carregar?')) {
      return;
    }
    try {
      final id = await _garantirAtividade();
      await session.api.carregar(id, d.id!);
      if (mounted) {
        setState(() {});
        mostrarMensagem(context, 'CT-e ${d.numeroCte ?? d.id} em carregamento.');
      }
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _marcarSelecionados() async {
    if (_selecionados.isEmpty) return;
    setState(() => _ocupado = true);
    try {
      final id = await _garantirAtividade();
      await session.api.carregarLote(id, _selecionados.toList());
      if (mounted) mostrarMensagem(context, '${_selecionados.length} CT-e(s) em carregamento.');
      setState(() {
        _selecionados.clear();
        _modoSelecao = false;
      });
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      if (mounted) setState(() => _ocupado = false);
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

  Future<void> _concluir() async {
    if (_atv == null) return;
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

  @override
  Widget build(BuildContext context) {
    final souParticipante = _atv != null && _atv!.souParticipanteAtivo(session.usuario?.id);
    final toggleSelecao = <Widget>[
      if (_disponiveis.isNotEmpty)
        IconButton(
          tooltip: _modoSelecao ? 'Sair da seleção' : 'Selecionar vários',
          icon: Icon(_modoSelecao ? Icons.close : Icons.checklist),
          onPressed: () => setState(() {
            _modoSelecao = !_modoSelecao;
            _selecionados.clear();
          }),
        ),
    ];
    return PopScope(
      canPop: !souParticipante,
      onPopInvoked: (didPop) {
        if (!didPop && mounted) {
          mostrarMensagem(context, 'Use "Sair" ou "Concluir" para fechar a atividade.');
        }
      },
      child: Scaffold(
        appBar: _atv == null
            ? AppBar(title: const Text('Carregamento'), actions: toggleSelecao)
            : appBarAtividade(
                context,
                titulo: 'Carregamento',
                iniciadaEm: _atv!.iniciadaEm,
                idAtividade: _atv!.id,
                aoMudar: () {
                  if (mounted) Navigator.pop(context);
                },
                acoesExtras: toggleSelecao,
              ),
        bottomNavigationBar: _barraInferior(souParticipante),
        body: _erro != null
            ? Center(child: Padding(padding: const EdgeInsets.all(16), child: Text(_erro!)))
            : _carregando
                ? const Center(child: CircularProgressIndicator())
                : RefreshIndicator(
                    onRefresh: _carregar,
                    child: ListView(
                      children: [
                        if (_modoSelecao) _barraSelecao(),
                        ..._disponiveis.map(_linhaDisponivel),
                        if (_marcados.isNotEmpty) _secaoMarcados(),
                        if (_disponiveis.isEmpty && _marcados.isEmpty)
                          const Padding(
                              padding: EdgeInsets.all(24),
                              child: Center(child: Text('Nada para carregar.'))),
                      ],
                    ),
                  ),
      ),
    );
  }

  Widget _barraSelecao() {
    final todosSel =
        _disponiveis.isNotEmpty && _disponiveis.every((d) => _selecionados.contains(d.id));
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Row(
        children: [
          Text('${_selecionados.length} selecionado(s)'),
          const Spacer(),
          TextButton.icon(
            icon: Icon(todosSel ? Icons.deselect : Icons.select_all),
            label: Text(todosSel ? 'Limpar' : 'Selecionar todos'),
            onPressed: () => setState(() {
              if (todosSel) {
                _selecionados.clear();
              } else {
                _selecionados.addAll(_disponiveis.map((d) => d.id!).whereType<int>());
              }
            }),
          ),
        ],
      ),
    );
  }

  Widget? _barraInferior(bool souParticipante) {
    if (_modoSelecao) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: botaoGrandeAtividade(
            icone: Icons.playlist_add_check,
            texto: 'Marcar ${_selecionados.length} para carregar',
            onPressed: (_selecionados.isEmpty || _ocupado) ? null : _marcarSelecionados,
          ),
        ),
      );
    }
    if (_atv == null) return null; // pré-atividade: ainda nada marcado
    final primaria = (souParticipante && _marcados.isNotEmpty)
        ? botaoGrandeAtividade(
            icone: Icons.check_circle,
            texto: 'Concluir carregamento',
            onPressed: _concluir,
            cor: Colors.green,
          )
        : null;
    return barraAtividadeCompartilhada(
      context,
      idAtividade: _atv!.id,
      ativos: _atv!.participantesAtivos,
      souParticipante: souParticipante,
      onParticipar: _participar,
      onSair: () => sairAtividade(context, _atv!.id, aoMudar: () {
        if (mounted) Navigator.pop(context);
      }),
      primaria: primaria,
    );
  }

  Widget _linhaDisponivel(DocumentoOperacional d) {
    final cross = d.status == 'NO_ARMAZEM';
    final sel = _selecionados.contains(d.id);
    return ListTile(
      leading: _modoSelecao
          ? Checkbox(
              value: sel,
              onChanged: (_) => setState(() => _alternar(d.id)),
            )
          : Icon(Icons.upload, color: cross ? Colors.purple : Colors.green),
      title: Text('CT-e ${d.numeroCte ?? d.id}'),
      subtitle: Text([
        if (cross) 'cross-dock direto',
        if (d.destinatario != null) d.destinatario!,
        if (d.cidadeDestino != null) d.cidadeDestino!,
      ].join(' · ')),
      onTap: _modoSelecao
          ? () => setState(() => _alternar(d.id))
          : () => _marcarUm(d),
    );
  }

  void _alternar(int? id) {
    if (id == null) return;
    if (_selecionados.contains(id)) {
      _selecionados.remove(id);
    } else {
      _selecionados.add(id);
    }
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
}
