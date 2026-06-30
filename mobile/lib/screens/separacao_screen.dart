import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../chave/chave_parser.dart';
import '../main.dart';
import '../models/box_destino.dart';
import '../models/models.dart';
import '../widgets/atividade_app_bar.dart';
import '../widgets/barra_atividade.dart';
import '../widgets/dialogos.dart';
import '../widgets/scanner_sheet.dart';
import 'atividade_actions.dart';

/// Separação por caminhão: escolhe o caminhão em descarga (ou já descarregado hoje),
/// abre a separação e marca os CT-es daquele caminhão — que vão direto pro Box
/// Distribuição. Dá pra ir separando o que já saiu do caminhão mesmo antes da descarga
/// concluir. Pessoas/chapa/sair/concluir igual à descarga de coleta.
class SeparacaoScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  const SeparacaoScreen({super.key, this.atividade});

  @override
  State<SeparacaoScreen> createState() => _SeparacaoScreenState();
}

class _SeparacaoScreenState extends State<SeparacaoScreen> {
  AtividadeResumo? _atv;
  List<CaminhaoEmDescarga> _caminhoes = [];
  List<DocumentoOperacional> _docs = []; // separáveis do caminhão
  List<DocumentoOperacional> _separados = []; // já SEPARADO_BOX nesta atividade
  List<LocalArmazem> _locais = [];
  final Set<int> _selecionados = {};
  bool _modoSelecao = false;
  bool _ocupado = false;
  bool _carregando = true;
  String _filtro = '';
  String? _erro;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
    if (_atv == null) {
      _carregarCaminhoes();
    } else {
      _carregarDocs();
    }
  }

  // ---- Passo 1: escolher o caminhão ----------------------------------
  Future<void> _carregarCaminhoes() async {
    setState(() => _carregando = true);
    try {
      final c = await session.api.caminhoesParaSeparar();
      setState(() {
        _caminhoes = c;
        _erro = null;
        _carregando = false;
      });
    } on ApiException catch (e) {
      setState(() {
        _erro = e.message;
        _carregando = false;
      });
    }
  }

  Future<void> _abrirCaminhao(CaminhaoEmDescarga c) async {
    if (!await confirmar(context, 'Separar caminhão',
        'Abrir a separação do caminhão ${c.placa ?? c.idViagem}?')) {
      return;
    }
    try {
      final atv = await session.api.abrirAtividade(
        tipo: 'SEPARACAO',
        idViagem: c.idViagem,
        placa: c.placa,
      );
      if (!mounted) return;
      setState(() => _atv = atv);
      await _carregarDocs();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  // ---- Passo 2: CT-es do caminhão ------------------------------------
  Future<void> _carregarDocs() async {
    setState(() => _carregando = true);
    try {
      final idViagem = _atv!.idViagemLegado;
      final docs = idViagem != null
          ? await session.api.separaveisDoCaminhao(idViagem)
          : await session.api.disponiveis('separar'); // legado: atividade sem viagem
      final separados = (await session.api.documentosDaAtividade(_atv!.id))
          .where((d) => d.status == 'SEPARADO_BOX')
          .toList();
      final locais = _locais.isEmpty ? await session.api.locais() : _locais;
      setState(() {
        _docs = docs;
        _separados = separados;
        _locais = locais;
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

  LocalArmazem? _boxDistribuicao() {
    for (final l in _locais) {
      if (l.ativo && l.codigo.toUpperCase() == BoxDestino.dist) return l;
    }
    return null;
  }

  Future<void> _participar() async {
    if (_atv == null) return;
    final ok = await entrarAtividade(context, _atv!.id);
    if (ok) {
      final a = await session.api.buscarAtividade(_atv!.id);
      if (mounted) setState(() => _atv = a);
    }
  }

  Future<void> _separar(DocumentoOperacional d) async {
    final local = _boxDistribuicao();
    if (local == null) {
      mostrarMensagem(context,
          'Box de Distribuição (DIST) não encontrado ou inativo para esta filial.',
          erro: true);
      return;
    }
    try {
      await session.api.separar(_atv!.id, d.id!, local.id);
      if (mounted) mostrarMensagem(context, 'CT-e ${d.numeroCte ?? d.id} → ${local.nome}');
      await _carregarDocs();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _marcarSelecionados() async {
    if (_selecionados.isEmpty) return;
    final local = _boxDistribuicao();
    if (local == null) {
      mostrarMensagem(context,
          'Box de Distribuição (DIST) não encontrado ou inativo para esta filial.',
          erro: true);
      return;
    }
    setState(() => _ocupado = true);
    try {
      await session.api.separarLote(_atv!.id, _selecionados.toList(), local.id);
      if (mounted) mostrarMensagem(context, '${_selecionados.length} CT-e(s) → ${local.nome}');
      setState(() {
        _selecionados.clear();
        _modoSelecao = false;
      });
      await _carregarDocs();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  DocumentoOperacional? _resolverBip(String raw) {
    final chave = ChaveParser.normalizar(raw);
    if (chave != null) {
      for (final d in _docs) {
        if (d.chaveNf == chave) return d;
      }
      final numero = ChaveParser.isCte(chave) ? ChaveParser.numero(chave) : null;
      if (numero != null) {
        for (final d in _docs) {
          if (d.numeroCte == numero) return d;
        }
      }
      return null;
    }
    final numero = int.tryParse(raw.replaceAll(RegExp(r'\D'), ''));
    if (numero == null) return null;
    for (final d in _docs) {
      if (d.numeroCte == numero) return d;
    }
    return null;
  }

  Future<void> _bipar() async {
    final raw = await abrirScanner(context, titulo: 'Bipar CT-e ou NF-e');
    if (raw == null) return;
    final doc = _resolverBip(raw);
    if (doc == null) {
      if (mounted) {
        mostrarMensagem(context, 'Documento bipado não está neste caminhão.', erro: true);
      }
      return;
    }
    await _separar(doc);
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

  Future<void> _concluir() async {
    if (_atv == null) return;
    await finalizarAtividade(context, _atv!.id, aoMudar: () {
      if (mounted) Navigator.pop(context);
    });
  }

  List<DocumentoOperacional> get _filtrados {
    final f = _filtro.toLowerCase();
    if (f.isEmpty) return _docs;
    return _docs.where((d) {
      return (d.numeroCte?.toString() ?? '').contains(f) ||
          (d.remetente ?? '').toLowerCase().contains(f) ||
          (d.destinatario ?? '').toLowerCase().contains(f) ||
          (d.cidadeDestino ?? '').toLowerCase().contains(f);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    if (_atv == null) return _telaCaminhoes();
    return _telaSeparacao();
  }

  // ---- Tela 1: escolha do caminhão -----------------------------------
  Widget _telaCaminhoes() {
    return Scaffold(
      appBar: AppBar(title: const Text('Separação · escolha o caminhão')),
      body: _carregando
          ? const Center(child: CircularProgressIndicator())
          : _erro != null
              ? Center(child: Padding(padding: const EdgeInsets.all(16), child: Text(_erro!)))
              : RefreshIndicator(
                  onRefresh: _carregarCaminhoes,
                  child: _caminhoes.isEmpty
                      ? ListView(children: const [
                          Padding(
                              padding: EdgeInsets.all(24),
                              child: Center(child: Text('Nenhum caminhão em descarga hoje.')))
                        ])
                      : ListView.builder(
                          padding: const EdgeInsets.all(12),
                          itemCount: _caminhoes.length,
                          itemBuilder: (_, i) {
                            final c = _caminhoes[i];
                            return Card(
                              child: ListTile(
                                leading: Icon(Icons.local_shipping,
                                    color: c.descargaAberta ? Colors.orange : Colors.green),
                                title: Text(c.placa ?? 'Viagem ${c.idViagem}',
                                    style: const TextStyle(fontWeight: FontWeight.bold)),
                                subtitle: Text(c.descargaAberta
                                    ? 'Descarregando agora'
                                    : 'Descarregado hoje'),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => _abrirCaminhao(c),
                              ),
                            );
                          },
                        ),
                ),
    );
  }

  // ---- Tela 2: separação do caminhão ---------------------------------
  Widget _telaSeparacao() {
    final souParticipante = _atv!.souParticipanteAtivo(session.usuario?.id);
    final docs = _filtrados;
    final toggle = <Widget>[
      if (_docs.isNotEmpty)
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
        appBar: appBarAtividade(
          context,
          titulo: 'Separação · ${_atv!.placaVeiculo ?? '#${_atv!.id}'}',
          iniciadaEm: _atv!.iniciadaEm,
          idAtividade: _atv!.id,
          aoMudar: () {
            if (mounted) Navigator.pop(context);
          },
          acoesExtras: toggle,
        ),
        floatingActionButton: _modoSelecao
            ? null
            : FloatingActionButton.extended(
                onPressed: _bipar,
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('Bipar'),
              ),
        bottomNavigationBar: _modoSelecao
            ? SafeArea(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: botaoGrandeAtividade(
                    icone: Icons.playlist_add_check,
                    texto: 'Marcar ${_selecionados.length} → Distribuição',
                    onPressed: (_selecionados.isEmpty || _ocupado) ? null : _marcarSelecionados,
                  ),
                ),
              )
            : barraAtividadeCompartilhada(
                context,
                idAtividade: _atv!.id,
                ativos: _atv!.participantesAtivos,
                souParticipante: souParticipante,
                onParticipar: _participar,
                onSair: () => sairAtividade(context, _atv!.id, aoMudar: () {
                  if (mounted) Navigator.pop(context);
                }),
                primaria: souParticipante
                    ? botaoGrandeAtividade(
                        icone: Icons.check_circle,
                        texto: 'Concluir separação',
                        onPressed: _concluir,
                        cor: Colors.green,
                      )
                    : null,
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
                            hintText: 'Filtrar por CT-e, remetente, destino...',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          onChanged: (v) => setState(() => _filtro = v),
                        ),
                      ),
                      Expanded(
                        child: RefreshIndicator(
                          onRefresh: _carregarDocs,
                          child: ListView(
                            children: [
                              ...docs.map(_linha),
                              if (_separados.isNotEmpty) _secaoSeparados(),
                              if (docs.isEmpty && _separados.isEmpty)
                                const Padding(
                                    padding: EdgeInsets.all(24),
                                    child: Center(child: Text('Nada para separar neste caminhão.'))),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
      ),
    );
  }

  Widget _linha(DocumentoOperacional d) {
    final sel = _selecionados.contains(d.id);
    final emDescarga = d.status == 'EM_DESCARGA';
    return ListTile(
      leading: _modoSelecao
          ? Checkbox(value: sel, onChanged: (_) => _alternar(d.id))
          : Icon(Icons.call_split, color: emDescarga ? Colors.purple : Colors.orange),
      title: Text('CT-e ${d.numeroCte ?? d.id}'),
      subtitle: Text([
        if (emDescarga) 'ainda no caminhão',
        if (d.remetente != null) d.remetente!,
        if (d.destinatario != null) d.destinatario!,
        if (d.cidadeDestino != null) d.cidadeDestino!,
      ].join(' · ')),
      trailing: _modoSelecao ? null : const Icon(Icons.chevron_right),
      onTap: _modoSelecao ? () => _alternar(d.id) : () => _separar(d),
    );
  }

  Widget _secaoSeparados() {
    return ExpansionTile(
      leading: const Icon(Icons.inventory_2, color: Colors.green),
      title: Text('Separados → Distribuição (${_separados.length})'),
      children: _separados
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
