import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/cronometro.dart';
import '../widgets/dialogos.dart';
import '../widgets/scanner_sheet.dart';

/// Registro de ocorrência de avaria, sempre amarrado a uma atividade (viagem).
/// Quando aberto pela grade da Home (`atividade == null`), o operador escolhe
/// primeiro a atividade aberta; quando aberto de dentro de uma atividade, já vem
/// com a viagem/placa no contexto.
class OcorrenciaScreen extends StatefulWidget {
  final AtividadeResumo? atividade;
  final String? motorista;

  const OcorrenciaScreen({super.key, this.atividade, this.motorista});

  @override
  State<OcorrenciaScreen> createState() => _OcorrenciaScreenState();
}

class _OcorrenciaScreenState extends State<OcorrenciaScreen> {
  static const _culpas = ['CARREGAMENTO', 'VIAGEM', 'DESCARREGAMENTO'];
  static const _rotuloCulpa = {
    'CARREGAMENTO': 'Carregamento',
    'VIAGEM': 'Viagem (motorista)',
    'DESCARREGAMENTO': 'Descarregamento',
  };

  final DateTime _inicio = DateTime.now();

  AtividadeResumo? _atividade;
  String? _motorista;

  final List<int> _ctes = [];
  final List<String> _fotos = [];
  final List<String> _fotosNf = [];
  final List<_ItemRow> _itens = [];

  String? _culpa;
  DateTime _dataIdent = DateTime.now();
  final _descricao = TextEditingController();
  final _quemCausou = TextEditingController();
  final _responsavel = TextEditingController();
  final _valorTotal = TextEditingController();

  bool _enviando = false;

  @override
  void initState() {
    super.initState();
    _atividade = widget.atividade;
    _motorista = widget.motorista;
    _itens.add(_ItemRow());
    if (_atividade != null) _prefixarCtes(_atividade!.id);
  }

  @override
  void dispose() {
    _descricao.dispose();
    _quemCausou.dispose();
    _responsavel.dispose();
    _valorTotal.dispose();
    for (final it in _itens) {
      it.dispose();
    }
    super.dispose();
  }

  /// Pré-carrega os CT-es já processados na atividade (facilita marcar os avariados).
  Future<void> _prefixarCtes(int idAtividade) async {
    try {
      final docs = await session.api.documentosDaAtividade(idAtividade);
      if (!mounted) return;
      setState(() {
        for (final d in docs) {
          if (d.numeroCte != null && !_ctes.contains(d.numeroCte)) {
            _ctes.add(d.numeroCte!);
          }
        }
      });
    } catch (_) {
      // Sem CT-es pré-carregados; o operador ainda pode bipar/digitar.
    }
  }

  Future<void> _biparCte() async {
    final raw = await abrirScanner(context, titulo: 'Bipar CT-e');
    if (raw == null) return;
    final numero = int.tryParse(raw.replaceAll(RegExp(r'\D'), ''));
    if (numero == null || numero <= 0) {
      if (mounted) mostrarMensagem(context, 'CT-e inválido: $raw', erro: true);
      return;
    }
    setState(() {
      if (!_ctes.contains(numero)) _ctes.add(numero);
    });
  }

  Future<void> _digitarCte() async {
    final n = await pedirNumero(context, 'Adicionar CT-e', 'Número do CT-e');
    if (n == null) return;
    setState(() {
      if (!_ctes.contains(n)) _ctes.add(n);
    });
  }

  Future<void> _tirarFoto(List<String> destino) async {
    final x = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 70);
    if (x != null) setState(() => destino.add(x.path));
  }

  Future<void> _escolherData() async {
    final d = await showDatePicker(
      context: context,
      initialDate: _dataIdent,
      firstDate: DateTime(2020),
      lastDate: DateTime.now().add(const Duration(days: 1)),
    );
    if (d == null || !mounted) return;
    final t = await showTimePicker(context: context, initialTime: TimeOfDay.fromDateTime(_dataIdent));
    if (!mounted) return;
    setState(() {
      _dataIdent = DateTime(d.year, d.month, d.day, t?.hour ?? _dataIdent.hour, t?.minute ?? _dataIdent.minute);
    });
  }

  Future<void> _enviar() async {
    if (_atividade == null) {
      mostrarMensagem(context, 'Escolha a atividade da ocorrência.', erro: true);
      return;
    }
    if (_culpa == null) {
      mostrarMensagem(context, 'Informe de quem é a culpa.', erro: true);
      return;
    }
    final itens = _itens
        .where((it) => it.nome.text.trim().isNotEmpty)
        .map((it) => {
              if (it.codigo.text.trim().isNotEmpty) 'codigo': it.codigo.text.trim(),
              'nome': it.nome.text.trim(),
              'quantidade': double.tryParse(it.quantidade.text.replaceAll(',', '.')) ?? 1,
            })
        .toList();

    setState(() => _enviando = true);
    try {
      await session.api.registrarAvaria(
        idAtividade: _atividade!.id,
        placa: _atividade!.placaVeiculo,
        motorista: _motorista,
        numerosCte: _ctes,
        descricao: _descricao.text.trim().isEmpty ? null : _descricao.text.trim(),
        culpa: _culpa,
        quemCausou: _quemCausou.text.trim().isEmpty ? null : _quemCausou.text.trim(),
        responsavelPagamento: _responsavel.text.trim().isEmpty ? null : _responsavel.text.trim(),
        valorTotal: double.tryParse(_valorTotal.text.replaceAll('.', '').replaceAll(',', '.')),
        dataIdentificacao: _dataIdent,
        duracaoSegundos: DateTime.now().difference(_inicio).inSeconds,
        itens: itens,
        fotos: _fotos,
        fotosNf: _fotosNf,
      );
      if (mounted) {
        mostrarMensagem(context, 'Avaria registrada.');
        Navigator.pop(context);
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } catch (_) {
      if (mounted) mostrarMensagem(context, 'Falha ao registrar a avaria.', erro: true);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_atividade == null) {
      return _seletorAtividade();
    }
    return Scaffold(
      appBar: AppBar(
        title: const Text('Registrar avaria'),
        actions: [
          Padding(
            padding: const EdgeInsets.only(right: 12),
            child: Center(
              child: Cronometro(
                inicio: _inicio,
                estilo: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14, color: Colors.white),
              ),
            ),
          ),
        ],
      ),
      body: ListView(
        padding: EdgeInsets.fromLTRB(16, 16, 16, 24 + MediaQuery.of(context).viewPadding.bottom),
        children: [
          _cardViagem(),
          const SizedBox(height: 16),
          _secao('CT-es avariados'),
          _chipsCtes(),
          const SizedBox(height: 16),
          _secao('Fotos da avaria'),
          _tiraFotos(_fotos, 'avaria'),
          const SizedBox(height: 16),
          _secao('Itens avariados'),
          ..._linhasItens(),
          TextButton.icon(
            onPressed: () => setState(() => _itens.add(_ItemRow())),
            icon: const Icon(Icons.add),
            label: const Text('Adicionar item'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: _valorTotal,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            decoration: const InputDecoration(
              labelText: 'Valor total da avaria (R\$)',
              border: OutlineInputBorder(),
              prefixText: 'R\$ ',
            ),
          ),
          const SizedBox(height: 16),
          _secao('Data/hora identificada'),
          OutlinedButton.icon(
            onPressed: _escolherData,
            icon: const Icon(Icons.event),
            label: Text(_fmtData(_dataIdent)),
          ),
          const SizedBox(height: 16),
          _secao('Responsabilidade'),
          SegmentedButton<String>(
            segments: _culpas
                .map((c) => ButtonSegment(value: c, label: Text(_rotuloCulpa[c]!, textAlign: TextAlign.center)))
                .toList(),
            selected: _culpa == null ? <String>{} : {_culpa!},
            emptySelectionAllowed: true,
            multiSelectionEnabled: false,
            showSelectedIcon: false,
            onSelectionChanged: (s) => setState(() => _culpa = s.isEmpty ? null : s.first),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _quemCausou,
            decoration: const InputDecoration(
                labelText: 'Quem causou (nome)', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _responsavel,
            decoration: const InputDecoration(
                labelText: 'Pagar para (destinatário/quem emite a NF)', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 16),
          _secao('Descrição'),
          TextField(
            controller: _descricao,
            maxLines: 3,
            decoration: const InputDecoration(
                labelText: 'Descrição da avaria', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 16),
          _secao('Nota fiscal (opcional)'),
          _tiraFotos(_fotosNf, 'nf'),
          const SizedBox(height: 24),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: _enviando ? null : _enviar,
            child: _enviando
                ? const SizedBox(height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Registrar avaria'),
          ),
        ],
      ),
    );
  }

  // ---- Seletor de atividade (entrada pela grade) -------------------------
  Widget _seletorAtividade() {
    return Scaffold(
      appBar: AppBar(title: const Text('Avaria · escolher viagem')),
      body: FutureBuilder<List<AtividadeResumo>>(
        future: session.api.atividadesAbertas(),
        builder: (context, snap) {
          if (snap.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return Padding(padding: const EdgeInsets.all(16), child: Text('Erro: ${snap.error}'));
          }
          final lista = snap.data ?? [];
          if (lista.isEmpty) {
            return const Padding(
              padding: EdgeInsets.all(24),
              child: Text('Nenhuma atividade aberta. A avaria precisa estar amarrada a uma viagem '
                  '(descarga, separação ou carregamento).'),
            );
          }
          return ListView(
            padding: const EdgeInsets.all(12),
            children: lista
                .map((a) => Card(
                      child: ListTile(
                        leading: const Icon(Icons.local_shipping),
                        title: Text('${a.rotuloTipo}${a.placaVeiculo != null ? ' · ${a.placaVeiculo}' : ''}'),
                        subtitle: Text('#${a.id}'),
                        onTap: () {
                          setState(() => _atividade = a);
                          _prefixarCtes(a.id);
                        },
                      ),
                    ))
                .toList(),
          );
        },
      ),
    );
  }

  Widget _cardViagem() {
    final a = _atividade!;
    return Card(
      color: Colors.blue.shade50,
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('${a.rotuloTipo} · #${a.id}', style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 4),
            Text('Placa: ${a.placaVeiculo ?? '—'}'
                '${_motorista != null ? '   ·   Motorista: $_motorista' : ''}'),
          ],
        ),
      ),
    );
  }

  Widget _secao(String titulo) => Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Text(titulo, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
      );

  Widget _chipsCtes() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Wrap(
          spacing: 8,
          runSpacing: 4,
          children: [
            for (final c in _ctes)
              Chip(
                label: Text('CT-e $c'),
                onDeleted: () => setState(() => _ctes.remove(c)),
              ),
            if (_ctes.isEmpty) const Text('Nenhum CT-e adicionado.'),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            OutlinedButton.icon(
                onPressed: _biparCte, icon: const Icon(Icons.qr_code_scanner), label: const Text('Bipar CT-e')),
            const SizedBox(width: 8),
            TextButton.icon(onPressed: _digitarCte, icon: const Icon(Icons.keyboard), label: const Text('Digitar')),
          ],
        ),
      ],
    );
  }

  Widget _tiraFotos(List<String> lista, String tag) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (lista.isNotEmpty)
          SizedBox(
            height: 96,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: lista.length,
              separatorBuilder: (_, __) => const SizedBox(width: 8),
              itemBuilder: (_, i) => Stack(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: Image.file(File(lista[i]), width: 96, height: 96, fit: BoxFit.cover),
                  ),
                  Positioned(
                    right: 0,
                    top: 0,
                    child: GestureDetector(
                      onTap: () => setState(() => lista.removeAt(i)),
                      child: const CircleAvatar(
                        radius: 12,
                        backgroundColor: Colors.black54,
                        child: Icon(Icons.close, size: 16, color: Colors.white),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        const SizedBox(height: 8),
        OutlinedButton.icon(
          onPressed: () => _tirarFoto(lista),
          icon: const Icon(Icons.camera_alt),
          label: Text(lista.isEmpty ? 'Tirar foto' : 'Adicionar outra'),
        ),
      ],
    );
  }

  List<Widget> _linhasItens() {
    return [
      for (int i = 0; i < _itens.length; i++)
        Padding(
          padding: const EdgeInsets.only(bottom: 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(
                width: 90,
                child: TextField(
                  controller: _itens[i].codigo,
                  decoration: const InputDecoration(labelText: 'Código', border: OutlineInputBorder(), isDense: true),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: TextField(
                  controller: _itens[i].nome,
                  decoration: const InputDecoration(labelText: 'Produto', border: OutlineInputBorder(), isDense: true),
                ),
              ),
              const SizedBox(width: 8),
              SizedBox(
                width: 64,
                child: TextField(
                  controller: _itens[i].quantidade,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  decoration: const InputDecoration(labelText: 'Qtd', border: OutlineInputBorder(), isDense: true),
                ),
              ),
              IconButton(
                icon: const Icon(Icons.remove_circle_outline),
                onPressed: _itens.length == 1
                    ? null
                    : () => setState(() {
                          _itens[i].dispose();
                          _itens.removeAt(i);
                        }),
              ),
            ],
          ),
        ),
    ];
  }

  String _fmtData(DateTime d) {
    String dois(int n) => n.toString().padLeft(2, '0');
    return '${dois(d.day)}/${dois(d.month)}/${d.year} ${dois(d.hour)}:${dois(d.minute)}';
  }
}

class _ItemRow {
  final codigo = TextEditingController();
  final nome = TextEditingController();
  final quantidade = TextEditingController(text: '1');

  void dispose() {
    codigo.dispose();
    nome.dispose();
    quantidade.dispose();
  }
}

/// Pede um número inteiro positivo (ex.: CT-e). Retorna null se cancelar/ inválido.
Future<int?> pedirNumero(BuildContext context, String titulo, String rotulo) async {
  final ctrl = TextEditingController();
  try {
    return await showDialog<int>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(titulo),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          decoration: InputDecoration(labelText: rotulo),
          onSubmitted: (v) => Navigator.pop(ctx, int.tryParse(v.trim())),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancelar')),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, int.tryParse(ctrl.text.trim())),
            child: const Text('Adicionar'),
          ),
        ],
      ),
    );
  } finally {
    ctrl.dispose();
  }
}
