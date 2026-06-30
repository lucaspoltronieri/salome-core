import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../chave/chave_parser.dart';
import '../../main.dart';
import '../../models/models.dart';
import '../../widgets/atividade_app_bar.dart';
import '../../widgets/barra_atividade.dart';
import '../../widgets/destino_picker.dart';
import '../../widgets/dialogos.dart';
import '../../widgets/scanner_sheet.dart';
import '../atividade_actions.dart';

/// Descarga de transferência. Marcar um CT-e o coloca "em descarga" (sai da grade
/// do caminhão) e guarda o destino; só ao **Concluir** os CT-es viram status final.
/// Dois modos: rápido (selecionar vários → 1 destino) e detalhado (um a um / câmera).
class DescargaScreen extends StatefulWidget {
  final AtividadeResumo atividade;
  const DescargaScreen({super.key, required this.atividade});

  @override
  State<DescargaScreen> createState() => _DescargaScreenState();
}

class _DescargaScreenState extends State<DescargaScreen> {
  late AtividadeResumo _atv;
  List<CteDescarga> _ctes = [];
  List<LocalArmazem> _locais = [];
  final Set<int> _marcados = {}; // idConhecimento já EM_DESCARGA (saíram da grade)
  final Set<int> _selecionados = {}; // seleção do modo rápido
  bool _modoSelecao = false;
  bool _carregando = true;
  bool _ocupado = false;
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
      final ctes = await session.api.ctesDisponiveis(_idAtividade);
      final docs = await session.api.documentosDaAtividade(_idAtividade);
      final locais = _locais.isEmpty ? await session.api.locais() : _locais;
      setState(() {
        _ctes = ctes;
        _locais = locais;
        _marcados
          ..clear()
          ..addAll(docs
              .where((d) => d.idConhecimentoLegado != null)
              .map((d) => d.idConhecimentoLegado!));
        _carregando = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _carregando = false);
        mostrarMensagem(context, e.message, erro: true);
      }
    }
  }

  // ---- Modo detalhado (um CT-e) ---------------------------------------
  Future<void> _registrar(CteDescarga cte) async {
    if (_marcados.contains(cte.idConhecimento)) {
      mostrarMensagem(context, 'CT-e ${cte.cte ?? cte.idConhecimento} já está em descarga.');
      return;
    }
    final box = await escolherDestino(context, _locais, OrigemDescarga.transferencia,
        titulo: 'Destino do CT-e ${cte.cte ?? ''}');
    if (box == null) return;
    try {
      await session.api.registrarDescarga(_idAtividade, cte.idConhecimento, box.id);
      setState(() => _marcados.add(cte.idConhecimento));
      if (mounted) mostrarMensagem(context, 'CT-e ${cte.cte ?? ''} → ${box.nome} (em descarga)');
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  // ---- Modo rápido (vários CT-es → 1 destino) -------------------------
  Future<void> _marcarSelecionados() async {
    if (_selecionados.isEmpty) return;
    final box = await escolherDestino(context, _locais, OrigemDescarga.transferencia,
        titulo: 'Destino de ${_selecionados.length} CT-e(s)');
    if (box == null) return;
    setState(() => _ocupado = true);
    try {
      await session.api.registrarDescargaLote(_idAtividade, _selecionados.toList(), box.id);
      if (mounted) {
        mostrarMensagem(context, '${_selecionados.length} CT-e(s) → ${box.nome} (em descarga)');
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

  Future<void> _bipar() async {
    final raw = await abrirScanner(context);
    if (raw == null) return;
    final chave = ChaveParser.normalizar(raw);
    final numero = chave != null ? ChaveParser.numero(chave) : int.tryParse(raw.trim());
    CteDescarga? achado;
    for (final c in _ctes) {
      if (c.cte != null && c.cte == numero) {
        achado = c;
        break;
      }
    }
    if (achado == null) {
      if (mounted) {
        mostrarMensagem(context, 'CT-e lido (nº $numero) não pertence a esta viagem.', erro: true);
      }
      return;
    }
    await _registrar(achado);
  }

  Future<void> _concluir() async {
    final semMarcacao = _semMarcacao;
    if (semMarcacao.isNotEmpty) {
      mostrarMensagem(
        context,
        'Ainda existem ${semMarcacao.length} CT-e(s) sem destino na descarga.',
        erro: true,
      );
      return;
    }
    if (!await confirmar(context, 'Concluir descarga',
        'Concluir a descarga? Os CT-es marcados viram o status final no armazém.')) {
      return;
    }
    try {
      final f = await session.api.concluir(_idAtividade);
      if (mounted) {
        mostrarMensagem(context, 'Descarga concluída (${f.totalParticipantes} pessoa(s)).');
        Navigator.pop(context);
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  List<CteDescarga> get _pendentes {
    final f = _filtro.toLowerCase();
    return _ctes.where((c) {
      if (_marcados.contains(c.idConhecimento)) return false;
      if (f.isEmpty) return true;
      return (c.cte?.toString() ?? '').contains(f) ||
          (c.notasFiscais ?? '').toLowerCase().contains(f) ||
          (c.destinatario ?? '').toLowerCase().contains(f) ||
          (c.cidadeDestino ?? '').toLowerCase().contains(f);
    }).toList();
  }

  List<CteDescarga> get _emDescarga =>
      _ctes.where((c) => _marcados.contains(c.idConhecimento)).toList();

  List<CteDescarga> get _semMarcacao =>
      _ctes.where((c) => !_marcados.contains(c.idConhecimento)).toList();

  @override
  Widget build(BuildContext context) {
    final total = _ctes.length;
    final feitos = _marcados.length;
    final pendentes = _pendentes;
    final souParticipante = _atv.souParticipanteAtivo(session.usuario?.id);
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
          titulo: 'Descarga · ${_atv.placaVeiculo ?? '#$_idAtividade'}',
          iniciadaEm: _atv.iniciadaEm,
          idAtividade: _idAtividade,
          aoMudar: () {
            if (mounted) Navigator.pop(context);
          },
          acoesExtras: [
            IconButton(
              tooltip: _modoSelecao ? 'Sair da seleção' : 'Selecionar vários',
              icon: Icon(_modoSelecao ? Icons.close : Icons.checklist),
              onPressed: () => setState(() {
                _modoSelecao = !_modoSelecao;
                _selecionados.clear();
              }),
            ),
          ],
        ),
        floatingActionButton: _modoSelecao
            ? null
            : FloatingActionButton.extended(
                onPressed: _bipar,
                icon: const Icon(Icons.qr_code_scanner),
                label: const Text('Bipar'),
              ),
        bottomNavigationBar: _barraInferior(pendentes, souParticipante),
        body: _carregando
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(
                    children: [
                      LinearProgressIndicator(value: total == 0 ? 0 : feitos / total),
                      const SizedBox(height: 6),
                      Text('$feitos de $total CT-es em descarga'),
                      const SizedBox(height: 8),
                      TextField(
                        decoration: const InputDecoration(
                          prefixIcon: Icon(Icons.search),
                          hintText: 'Filtrar por CT-e, NF, destino...',
                          border: OutlineInputBorder(),
                          isDense: true,
                        ),
                        onChanged: (v) => setState(() => _filtro = v),
                      ),
                      if (_modoSelecao) _barraSelecao(pendentes),
                    ],
                  ),
                ),
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: _carregar,
                    child: ListView(
                      children: [
                        ...pendentes.map(_linhaPendente),
                        if (_emDescarga.isNotEmpty) _secaoEmDescarga(),
                        if (pendentes.isEmpty && _emDescarga.isEmpty)
                          const Padding(
                              padding: EdgeInsets.all(24),
                              child: Center(child: Text('Nenhum CT-e nesta viagem.'))),
                      ],
                    ),
                  ),
                ),
              ],
            ),
      ),
    );
  }

  Widget _barraSelecao(List<CteDescarga> pendentes) {
    final todosSel = pendentes.isNotEmpty && pendentes.every((c) => _selecionados.contains(c.idConhecimento));
    return Padding(
      padding: const EdgeInsets.only(top: 8),
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
                _selecionados.addAll(pendentes.map((c) => c.idConhecimento));
              }
            }),
          ),
        ],
      ),
    );
  }

  Widget? _barraInferior(List<CteDescarga> pendentes, bool souParticipante) {
    if (_modoSelecao) {
      return SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: botaoGrandeAtividade(
            icone: Icons.playlist_add_check,
            texto: 'Marcar ${_selecionados.length} → destino',
            onPressed: (_selecionados.isEmpty || _ocupado) ? null : _marcarSelecionados,
          ),
        ),
      );
    }
    Widget? primaria;
    if (souParticipante && _ctes.isNotEmpty) {
      final todosMarcados = _semMarcacao.isEmpty;
      primaria = botaoGrandeAtividade(
        icone: todosMarcados ? Icons.check_circle : Icons.pending_actions,
        texto: todosMarcados
            ? 'Concluir descarga'
            : 'Marque todos (${_marcados.length}/${_ctes.length})',
        onPressed: todosMarcados ? _concluir : null,
        cor: todosMarcados ? Colors.green : null,
      );
    }
    return barraAtividadeCompartilhada(
      context,
      idAtividade: _idAtividade,
      ativos: _atv.participantesAtivos,
      souParticipante: souParticipante,
      onParticipar: _participar,
      onSair: () => sairAtividade(context, _idAtividade, aoMudar: () {
        if (mounted) Navigator.pop(context);
      }),
      primaria: primaria,
    );
  }

  Widget _linhaPendente(CteDescarga c) {
    final sel = _selecionados.contains(c.idConhecimento);
    return ListTile(
      leading: _modoSelecao
          ? Checkbox(
              value: sel,
              onChanged: (_) => setState(() => _alternar(c.idConhecimento)),
            )
          : const Icon(Icons.radio_button_unchecked, color: Colors.grey),
      title: Text('CT-e ${c.cte ?? c.idConhecimento}'),
      subtitle: Text([
        if (c.destinatario != null) c.destinatario!,
        if (c.cidadeDestino != null) c.cidadeDestino!,
        '${c.volumes.toStringAsFixed(0)} vol · ${c.peso.toStringAsFixed(0)} kg',
      ].join(' · ')),
      onTap: _modoSelecao
          ? () => setState(() => _alternar(c.idConhecimento))
          : () => _registrar(c),
    );
  }

  void _alternar(int idConhecimento) {
    if (_selecionados.contains(idConhecimento)) {
      _selecionados.remove(idConhecimento);
    } else {
      _selecionados.add(idConhecimento);
    }
  }

  Widget _secaoEmDescarga() {
    return ExpansionTile(
      initiallyExpanded: false,
      leading: const Icon(Icons.local_shipping, color: Colors.orange),
      title: Text('Em descarga (${_emDescarga.length})'),
      children: _emDescarga
          .map((c) => ListTile(
                dense: true,
                leading: const Icon(Icons.check_circle, color: Colors.green),
                title: Text('CT-e ${c.cte ?? c.idConhecimento}'),
                subtitle: Text(c.destinatario ?? c.cidadeDestino ?? ''),
              ))
          .toList(),
    );
  }
}
