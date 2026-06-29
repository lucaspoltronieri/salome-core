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

/// Separação: lista os documentos disponíveis (NO_ARMAZEM) e separa enviando para
/// o box de Distribuição. Igual à descarga: dá pra **filtrar** (remetente/destinatário/
/// CT-e), **selecionar vários** e mandar todos pra Distribuição de uma vez, ou separar
/// um a um (toque/bipe).
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
  final Set<int> _selecionados = {};
  bool _modoSelecao = false;
  bool _ocupado = false;
  bool _carregando = true;
  String _erro = '';
  String _filtro = '';

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
    _init();
  }

  Future<void> _init() async {
    // NÃO abre atividade só por entrar na tela. A atividade (e a cronometragem)
    // só nasce quando o operador separa o primeiro CT-e — ver _separar()/_marcarSelecionados().
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
    final docs = await session.api.disponiveis('separar');
    final locais = _locais.isEmpty ? await session.api.locais() : _locais;
    setState(() {
      _docs = docs;
      _locais = locais;
      _carregando = false;
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

  LocalArmazem? _boxDistribuicao() {
    for (final l in _locais) {
      if (l.ativo && l.codigo.toUpperCase() == BoxDestino.dist) return l;
    }
    return null;
  }

  Future<int> _garantirAtividade() async {
    _atv ??= await session.api.abrirAtividade(tipo: 'SEPARACAO');
    return _atv!.id;
  }

  // ---- Modo detalhado (um CT-e) ---------------------------------------
  Future<void> _separar(DocumentoOperacional d) async {
    final local = _boxDistribuicao();
    if (local == null) {
      mostrarMensagem(context,
          'Box de Distribuição (DIST) não encontrado ou inativo para esta filial.',
          erro: true);
      return;
    }
    try {
      final id = await _garantirAtividade();
      await session.api.separar(id, d.id!, local.id);
      if (mounted) {
        setState(() {}); // revela o cabeçalho/ações da atividade
        mostrarMensagem(context, 'CT-e ${d.numeroCte ?? d.id} → ${local.nome}');
      }
      await _carregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  // ---- Modo rápido (vários CT-es → Distribuição) ----------------------
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
      final id = await _garantirAtividade();
      await session.api.separarLote(id, _selecionados.toList(), local.id);
      if (mounted) {
        mostrarMensagem(context, '${_selecionados.length} CT-e(s) → ${local.nome}');
      }
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

  DocumentoOperacional? _resolverBip(String raw) {
    final chave = ChaveParser.normalizar(raw);
    if (chave != null) {
      for (final d in _docs) {
        if (d.chaveNf == chave) return d;
      }
      final numero =
          ChaveParser.isCte(chave) ? ChaveParser.numero(chave) : null;
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
        mostrarMensagem(
            context, 'Documento bipado não está disponível para separação.',
            erro: true);
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

  Future<void> _finalizar() async {
    if (_atv == null) return;
    await finalizarAtividade(context, _atv!.id, aoMudar: () {
      if (mounted) Navigator.pop(context);
    });
  }

  Future<void> _cancelar() async {
    if (_atv == null) return;
    await cancelarAtividade(context, _atv!.id, aoMudar: () {
      if (mounted) Navigator.pop(context);
    });
  }

  @override
  Widget build(BuildContext context) {
    final emAtividade = _atv != null;
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
      canPop: !emAtividade,
      onPopInvoked: (didPop) {
        if (!didPop && mounted) {
          mostrarMensagem(context, 'Use "Finalizar" ou "Cancelar" para fechar a atividade.');
        }
      },
      child: Scaffold(
        appBar: emAtividade
            ? appBarAtividade(
                context,
                titulo: 'Separação',
                iniciadaEm: _atv!.iniciadaEm,
                idAtividade: _atv!.id,
                mostrarCancelar: false,
                acoesExtras: toggle,
              )
            : AppBar(title: const Text('Separação'), actions: toggle),
        floatingActionButton: _modoSelecao
            ? null
            : FloatingActionButton.extended(
                onPressed: _bipar,
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('Bipar'),
              ),
        bottomNavigationBar: _barraInferior(),
        body: _erro.isNotEmpty
            ? Center(child: Padding(padding: const EdgeInsets.all(16), child: Text(_erro)))
            : _carregando
                ? const Center(child: CircularProgressIndicator())
                : Column(
                    children: [
                      if (_docs.isNotEmpty)
                        Padding(
                          padding: const EdgeInsets.all(12),
                          child: TextField(
                            decoration: const InputDecoration(
                              prefixIcon: Icon(Icons.search),
                              hintText: 'Filtrar por CT-e, remetente, destinatário...',
                              border: OutlineInputBorder(),
                              isDense: true,
                            ),
                            onChanged: (v) => setState(() => _filtro = v),
                          ),
                        ),
                      if (_modoSelecao) _barraSelecao(),
                      Expanded(
                        child: RefreshIndicator(
                          onRefresh: _carregar,
                          child: _filtrados.isEmpty
                              ? ListView(children: const [
                                  Padding(
                                      padding: EdgeInsets.all(24),
                                      child: Center(child: Text('Nada para separar.')))
                                ])
                              : ListView.builder(
                                  itemCount: _filtrados.length,
                                  itemBuilder: (_, i) => _linha(_filtrados[i]),
                                ),
                        ),
                      ),
                    ],
                  ),
      ),
    );
  }

  Widget _barraSelecao() {
    final ids = _filtrados.map((d) => d.id).whereType<int>().toList();
    final todosSel = ids.isNotEmpty && ids.every(_selecionados.contains);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          Text('${_selecionados.length} selecionado(s)'),
          const Spacer(),
          TextButton.icon(
            icon: Icon(todosSel ? Icons.deselect : Icons.select_all),
            label: Text(todosSel ? 'Limpar' : 'Selecionar todos'),
            onPressed: () => setState(() {
              if (todosSel) {
                _selecionados.removeAll(ids);
              } else {
                _selecionados.addAll(ids);
              }
            }),
          ),
        ],
      ),
    );
  }

  Widget _linha(DocumentoOperacional d) {
    final sel = _selecionados.contains(d.id);
    return ListTile(
      leading: _modoSelecao
          ? Checkbox(value: sel, onChanged: (_) => _alternar(d.id))
          : const Icon(Icons.call_split, color: Colors.orange),
      title: Text('CT-e ${d.numeroCte ?? d.id}'),
      subtitle: Text([
        if (d.remetente != null) d.remetente!,
        if (d.destinatario != null) d.destinatario!,
        if (d.cidadeDestino != null) d.cidadeDestino!,
      ].join(' · ')),
      trailing: _modoSelecao ? null : const Icon(Icons.chevron_right),
      onTap: _modoSelecao ? () => _alternar(d.id) : () => _separar(d),
    );
  }

  Widget? _barraInferior() {
    if (_modoSelecao) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: botaoGrandeAtividade(
            icone: Icons.playlist_add_check,
            texto: 'Marcar ${_selecionados.length} → Distribuição',
            onPressed: (_selecionados.isEmpty || _ocupado) ? null : _marcarSelecionados,
          ),
        ),
      );
    }
    if (_atv == null) return null;
    return SafeArea(
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
            TextButton(onPressed: _cancelar, child: const Text('Cancelar atividade')),
          ],
        ),
      ),
    );
  }
}
