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
  /// Opcionais: preenchidos quando vem da Tela 1 (CaminhaoEmDescarga já enriquecido).
  /// Se nulos (ex. reaberta via Home), a tela busca via manifestoDaViagem() como fallback.
  final String? origemInicial;
  final String? motoristaInicial;
  final String? dataBaixaInicial;
  final String? horaBaixaInicial;
  final int? qtdManifestosInicial;
  final bool? descargaAbertaInicial;

  const SeparacaoScreen({
    super.key,
    this.atividade,
    this.origemInicial,
    this.motoristaInicial,
    this.dataBaixaInicial,
    this.horaBaixaInicial,
    this.qtdManifestosInicial,
    this.descargaAbertaInicial,
  });

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
  final TextEditingController _filtroCtrl = TextEditingController();
  String? _erro;

  String? _origem;
  String? _motorista;
  String? _dataBaixa;
  String? _horaBaixa;
  int _qtdManifestos = 1;
  bool? _descargaAberta;

  @override
  void initState() {
    super.initState();
    _atv = widget.atividade;
    _origem = widget.origemInicial;
    _motorista = widget.motoristaInicial;
    _dataBaixa = widget.dataBaixaInicial;
    _horaBaixa = widget.horaBaixaInicial;
    _qtdManifestos = widget.qtdManifestosInicial ?? 1;
    _descargaAberta = widget.descargaAbertaInicial;
    if (_atv == null) {
      _carregarCaminhoes();
    } else {
      _carregarDocs();
    }
  }

  /// "Ainda descarregando" quando reaberta sem o status conhecido: qualquer documento
  /// EM_DESCARGA na lista indica que ainda tem mercadoria saindo do caminhão.
  bool get _aindaDescarregando => _descargaAberta ?? _docs.any((d) => d.status == 'EM_DESCARGA');

  @override
  void dispose() {
    _filtroCtrl.dispose();
    super.dispose();
  }

  /// Limpa o filtro (texto + campo) para a lista do que falta separar reaparecer
  /// inteira após marcar — o TextField é controlado, então some o texto antigo.
  void _limparFiltro() {
    if (_filtro.isEmpty && _filtroCtrl.text.isEmpty) return;
    setState(() {
      _filtro = '';
      _filtroCtrl.clear();
    });
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
      setState(() {
        _atv = atv;
        _origem = c.origem;
        _motorista = c.motorista;
        _dataBaixa = c.dataBaixa;
        _horaBaixa = c.horaBaixa;
        _qtdManifestos = c.qtdManifestos;
        _descargaAberta = c.descargaAberta;
      });
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
      // Sem os dados iniciais (reaberta via Home, sem o CaminhaoEmDescarga da Tela 1):
      // busca a data de chegada/manifesto via fallback. Falha silenciosa (só omite o chip).
      if (_dataBaixa == null && idViagem != null) {
        try {
          final m = await session.api.manifestoDaViagem(idViagem);
          _dataBaixa = m.dataBaixa;
          _horaBaixa = m.horaBaixa;
          _qtdManifestos = m.qtdManifestos;
        } on ApiException {
          // omite o chip
        }
      }
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
      _limparFiltro();
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
      _limparFiltro();
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
    // O restante que ainda não foi marcado é considerado separado neste momento
    // (pode ser só visual — o físico já está separado). Marca tudo em lote e finaliza.
    final pendentes = List<DocumentoOperacional>.from(_docs);
    if (pendentes.isNotEmpty) {
      if (!await confirmar(context, 'Concluir separação',
          'Concluir a separação? Os ${pendentes.length} CT-e(s) restantes serão considerados separados.')) {
        return;
      }
      final local = _boxDistribuicao();
      if (local == null) {
        if (mounted) {
          mostrarMensagem(context,
              'Box de Distribuição (DIST) não encontrado ou inativo para esta filial.',
              erro: true);
        }
        return;
      }
      setState(() => _ocupado = true);
      try {
        await session.api
            .separarLote(_atv!.id, pendentes.map((d) => d.id!).toList(), local.id);
      } on ApiException catch (e) {
        if (mounted) {
          setState(() => _ocupado = false);
          mostrarMensagem(context, e.message, erro: true);
        }
        return;
      }
      if (mounted) setState(() => _ocupado = false);
    } else {
      if (!await confirmar(
          context, 'Concluir separação', 'Concluir a separação deste caminhão?')) {
        return;
      }
    }
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
                                title: Row(children: [
                                  Flexible(
                                      child: Text(c.placa ?? 'Viagem ${c.idViagem}',
                                          style: const TextStyle(fontWeight: FontWeight.bold))),
                                  if (c.qtdManifestos > 1) ...[
                                    const SizedBox(width: 6),
                                    _badgeManifesto(c.qtdManifestos),
                                  ],
                                ]),
                                subtitle: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    if (c.origem != null) Text(c.origem!),
                                    if (c.motorista != null) Text(c.motorista!),
                                    if (c.qtdCtes > 0)
                                      Text(
                                          '${c.qtdCtes} CT-es · ${c.volumes.toStringAsFixed(0)} vol · ${c.peso.toStringAsFixed(0)} kg'),
                                    Row(children: [
                                      Icon(c.descargaAberta ? Icons.local_shipping : Icons.check_circle,
                                          size: 14, color: c.descargaAberta ? Colors.orange : Colors.green),
                                      const SizedBox(width: 4),
                                      Text(c.descargaAberta ? 'Descarregando agora' : 'Descarregado',
                                          style: TextStyle(
                                              color: c.descargaAberta ? Colors.orange : Colors.green,
                                              fontWeight: FontWeight.bold)),
                                      if (!c.descargaAberta && c.dataBaixa != null) ...[
                                        const SizedBox(width: 6),
                                        Text('· ${_haQuanto(c.dataBaixa!, c.horaBaixa)}',
                                            style: const TextStyle(color: Colors.grey)),
                                      ],
                                    ]),
                                  ],
                                ),
                                isThreeLine: true,
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
    final feitos = _separados.length;
    final total = _docs.length + _separados.length;
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
                        child: Column(
                          children: [
                            _infoCabecalho(),
                            LinearProgressIndicator(
                                value: total == 0 ? 0 : feitos / total),
                            const SizedBox(height: 6),
                            Text('$feitos de $total separados'),
                            const SizedBox(height: 8),
                            TextField(
                              controller: _filtroCtrl,
                              decoration: const InputDecoration(
                                prefixIcon: Icon(Icons.search),
                                hintText: 'Filtrar por CT-e, remetente, destino...',
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

  Widget _infoCabecalho() {
    final semNada = _origem == null && _motorista == null && _dataBaixa == null;
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Wrap(
        spacing: 8,
        runSpacing: 4,
        children: [
          Chip(
            avatar: Icon(_aindaDescarregando ? Icons.local_shipping : Icons.check_circle,
                size: 16, color: _aindaDescarregando ? Colors.orange : Colors.green),
            label: Text(_aindaDescarregando ? 'Descarregando' : 'Descarregado'),
            backgroundColor: (_aindaDescarregando ? Colors.orange : Colors.green).withOpacity(.12),
          ),
          if (!_aindaDescarregando && _dataBaixa != null)
            Chip(label: Text(_haQuanto(_dataBaixa!, _horaBaixa))),
          if (_origem != null) Chip(avatar: const Icon(Icons.route, size: 16), label: Text(_origem!)),
          if (_motorista != null) Chip(avatar: const Icon(Icons.person, size: 16), label: Text(_motorista!)),
          if (_dataBaixa != null)
            Chip(
              avatar: const Icon(Icons.event_available, size: 16),
              label: Text('Chegada: $_dataBaixa ${_horaBaixa ?? ''}'),
            ),
          if (!semNada)
            Chip(
              avatar: const Icon(Icons.description, size: 16),
              label: Row(mainAxisSize: MainAxisSize.min, children: [
                const Text('Manifesto'),
                if (_qtdManifestos > 1) ...[const SizedBox(width: 6), _badgeManifesto(_qtdManifestos)],
              ]),
            ),
        ],
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

Widget _badgeManifesto(int n) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(color: Colors.blue.shade700, borderRadius: BorderRadius.circular(10)),
      child: Text('×$n',
          style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
    );

/// "há Xh" a partir de dataBaixa (yyyy-MM-dd) + horaBaixa (HH:mm:ss) — cálculo estático,
/// não usa o widget Cronometro (pensado pra timers ativos, não pra um evento passado).
String _haQuanto(String dataBaixa, String? horaBaixa) {
  final dt = DateTime.tryParse('$dataBaixa ${horaBaixa ?? '00:00:00'}');
  if (dt == null) return '';
  final diff = DateTime.now().difference(dt);
  if (diff.inMinutes < 60) return 'há ${diff.inMinutes} min';
  if (diff.inHours < 24) return 'há ${diff.inHours}h';
  return 'há ${diff.inDays}d';
}
