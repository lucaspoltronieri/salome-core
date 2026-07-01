import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../main.dart';
import '../../models/models.dart';
import '../../widgets/dialogos.dart';
import 'descarga_screen.dart';

/// Viagens de transferência aguardando descarga, agrupadas por idViagem (caminhão).
class ViagensScreen extends StatefulWidget {
  const ViagensScreen({super.key});

  @override
  State<ViagensScreen> createState() => _ViagensScreenState();
}

class _ViagensScreenState extends State<ViagensScreen> {
  late Future<List<_ViagemGrupo>> _future;

  @override
  void initState() {
    super.initState();
    _recarregar();
  }

  void _recarregar() {
    setState(() => _future = _carregar());
  }

  Future<List<_ViagemGrupo>> _carregar() async {
    final viagens = await session.api.viagensAguardando();
    return _agrupar(viagens);
  }

  Future<void> _abrir(_ViagemGrupo g) async {
    if (!await confirmar(context, 'Abrir descarga',
        'Abrir a descarga da viagem ${g.placa ?? g.chave} (${g.qtdCtes} CT-es)?')) {
      return;
    }
    try {
      final atv = await session.api.abrirAtividade(
        tipo: 'DESCARGA_TRANSFERENCIA',
        idViagem: g.idViagem,
        placa: g.placa,
      );
      if (!mounted) return;
      await Navigator.push(
          context,
          MaterialPageRoute(
              builder: (_) => DescargaScreen(
                    atividade: atv,
                    origemInicial: g.origem,
                    motoristaInicial: g.motorista,
                    dataBaixaInicial: g.dataBaixa,
                    horaBaixaInicial: g.horaBaixa,
                    qtdManifestosInicial: g.manifestos,
                  )));
      _recarregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Viagens aguardando')),
      body: RefreshIndicator(
        onRefresh: () async => _recarregar(),
        child: FutureBuilder<List<_ViagemGrupo>>(
          future: _future,
          builder: (context, snap) {
            if (snap.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }
            if (snap.hasError) {
              return ListView(children: [
                Padding(padding: const EdgeInsets.all(16), child: Text('Erro: ${snap.error}'))
              ]);
            }
            final lista = snap.data ?? [];
            if (lista.isEmpty) {
              return ListView(children: const [
                Padding(padding: EdgeInsets.all(24), child: Center(child: Text('Nenhuma viagem aguardando.')))
              ]);
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: lista.length,
              itemBuilder: (_, i) {
                final g = lista[i];
                return Card(
                  child: ListTile(
                    leading: const Icon(Icons.local_shipping, color: Colors.blue),
                    title: Row(children: [
                      Flexible(
                          child: Text(g.placa ?? 'Viagem ${g.chave}',
                              style: const TextStyle(fontWeight: FontWeight.bold))),
                      if (g.manifestos > 1) ...[const SizedBox(width: 6), _badgeManifesto(g.manifestos)],
                    ]),
                    subtitle: Text([
                      if (g.origem != null) g.origem!,
                      if (g.motorista != null) g.motorista!,
                      '${g.qtdCtes} CT-es · ${g.volumes.toStringAsFixed(0)} vol · ${g.peso.toStringAsFixed(0)} kg',
                      if (g.dataBaixa != null) 'Baixa: ${g.dataBaixa} ${g.horaBaixa ?? ''}',
                    ].join('\n')),
                    isThreeLine: true,
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _abrir(g),
                  ),
                );
              },
            );
          },
        ),
      ),
    );
  }
}

class _ViagemGrupo {
  final int chave;
  final int? idViagem;
  final String? placa;
  final String? motorista;
  final String? origem;
  int qtdCtes = 0;
  double volumes = 0;
  double peso = 0;
  String? dataBaixa;
  String? horaBaixa;
  int manifestos = 0;
  _ViagemGrupo(this.chave, this.idViagem, this.placa, this.motorista, this.origem);
}

List<_ViagemGrupo> _agrupar(List<ViagemAguardando> viagens) {
  final mapa = <int, _ViagemGrupo>{};
  for (final v in viagens) {
    final chave = v.idViagem ?? v.idViagemTransferencia;
    final g = mapa.putIfAbsent(
        chave, () => _ViagemGrupo(chave, v.idViagem, v.placa, v.motorista, v.origem));
    g.qtdCtes += v.qtdCtes;
    g.volumes += v.volumes;
    g.peso += v.peso;
    g.manifestos += 1;
    // mantém a baixa mais recente do grupo (mesmo critério do painel TV: agruparViagens()).
    final chaveOrd = '${v.dataBaixa} ${v.horaBaixa ?? ''}';
    final chaveAtual = '${g.dataBaixa} ${g.horaBaixa ?? ''}';
    if (g.dataBaixa == null || chaveOrd.compareTo(chaveAtual) > 0) {
      g.dataBaixa = v.dataBaixa;
      g.horaBaixa = v.horaBaixa;
    }
  }
  return mapa.values.toList();
}

Widget _badgeManifesto(int n) => Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
      decoration: BoxDecoration(color: Colors.blue.shade700, borderRadius: BorderRadius.circular(10)),
      child: Text('×$n',
          style: const TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold)),
    );
