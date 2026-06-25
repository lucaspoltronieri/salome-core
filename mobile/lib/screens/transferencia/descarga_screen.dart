import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../chave/chave_parser.dart';
import '../../main.dart';
import '../../models/models.dart';
import '../../widgets/destino_picker.dart';
import '../../widgets/dialogos.dart';
import '../../widgets/scanner_sheet.dart';
import '../atividade_actions.dart';

/// Descarga de transferência: lista os CT-es da viagem, marca o destino (box) de
/// cada um por toque ou câmera, e finaliza. Bipar é idempotente.
class DescargaScreen extends StatefulWidget {
  final AtividadeResumo atividade;
  const DescargaScreen({super.key, required this.atividade});

  @override
  State<DescargaScreen> createState() => _DescargaScreenState();
}

class _DescargaScreenState extends State<DescargaScreen> {
  List<CteDescarga> _ctes = [];
  List<LocalArmazem> _locais = [];
  final Set<int> _bipados = {}; // idConhecimento já descarregado
  bool _carregando = true;
  String _filtro = '';

  int get _idAtividade => widget.atividade.id;

  @override
  void initState() {
    super.initState();
    _carregar();
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
        _bipados
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

  Future<void> _registrar(CteDescarga cte) async {
    if (_bipados.contains(cte.idConhecimento)) {
      mostrarMensagem(context, 'CT-e ${cte.cte ?? cte.idConhecimento} já descarregado.');
      return;
    }
    final box = await escolherDestino(context, _locais, OrigemDescarga.transferencia,
        titulo: 'Destino do CT-e ${cte.cte ?? ''}');
    if (box == null) return;
    try {
      await session.api.registrarDescarga(_idAtividade, cte.idConhecimento, box.id);
      setState(() => _bipados.add(cte.idConhecimento));
      if (mounted) mostrarMensagem(context, 'CT-e ${cte.cte ?? ''} → ${box.nome}');
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
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

  List<CteDescarga> get _visiveis {
    if (_filtro.isEmpty) return _ctes;
    final f = _filtro.toLowerCase();
    return _ctes.where((c) {
      return (c.cte?.toString() ?? '').contains(f) ||
          (c.notasFiscais ?? '').toLowerCase().contains(f) ||
          (c.destinatario ?? '').toLowerCase().contains(f) ||
          (c.cidadeDestino ?? '').toLowerCase().contains(f);
    }).toList();
  }

  @override
  Widget build(BuildContext context) {
    final total = _ctes.length;
    final feitos = _ctes.where((c) => _bipados.contains(c.idConhecimento)).length;
    return Scaffold(
      appBar: AppBar(
        title: Text('Descarga · ${widget.atividade.placaVeiculo ?? '#$_idAtividade'}'),
        actions: [
          acoesAtividadeMenu(context, _idAtividade, aoMudar: () {
            if (mounted) Navigator.pop(context);
          }),
        ],
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _bipar,
        icon: const Icon(Icons.qr_code_scanner),
        label: const Text('Bipar'),
      ),
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
                      Text('$feitos de $total CT-es descarregados'),
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
                    ],
                  ),
                ),
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: _carregar,
                    child: ListView.builder(
                      itemCount: _visiveis.length,
                      itemBuilder: (_, i) {
                        final c = _visiveis[i];
                        final feito = _bipados.contains(c.idConhecimento);
                        return ListTile(
                          leading: Icon(feito ? Icons.check_circle : Icons.radio_button_unchecked,
                              color: feito ? Colors.green : Colors.grey),
                          title: Text('CT-e ${c.cte ?? c.idConhecimento}'),
                          subtitle: Text([
                            if (c.destinatario != null) c.destinatario!,
                            if (c.cidadeDestino != null) c.cidadeDestino!,
                            '${c.volumes.toStringAsFixed(0)} vol · ${c.peso.toStringAsFixed(0)} kg',
                          ].join(' · ')),
                          onTap: () => _registrar(c),
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
