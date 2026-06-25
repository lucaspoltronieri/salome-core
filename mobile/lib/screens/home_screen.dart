import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import '../widgets/dialogos.dart';
import 'carregamento_screen.dart';
import 'coleta/coleta_screen.dart';
import 'coleta/viagens_coleta_screen.dart';
import 'ocorrencia_screen.dart';
import 'outras_screen.dart';
import 'separacao_screen.dart';
import 'transferencia/descarga_screen.dart';
import 'transferencia/viagens_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  late Future<List<AtividadeResumo>> _abertas;

  @override
  void initState() {
    super.initState();
    _recarregar();
  }

  void _recarregar() {
    setState(() => _abertas = session.api.atividadesAbertas());
  }

  Future<void> _abrir(Widget tela) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => tela));
    _recarregar();
  }

  Future<void> _reabrir(AtividadeResumo a) async {
    switch (a.tipo) {
      case 'DESCARGA_TRANSFERENCIA':
        await _abrir(DescargaScreen(atividade: a));
        break;
      case 'DESCARGA_COLETA':
        await _abrir(ColetaScreen(atividade: a));
        break;
      case 'SEPARACAO':
        await _abrir(SeparacaoScreen(atividade: a));
        break;
      case 'CARREGAMENTO':
        await _abrir(CarregamentoScreen(atividade: a));
        break;
      default:
        await _abrir(OutrasScreen(atividade: a));
    }
  }

  Future<void> _finalizar(AtividadeResumo a) async {
    if (!await confirmar(context, 'Finalizar', 'Finalizar a atividade #${a.id}?')) return;
    try {
      final f = await session.api.finalizar(a.id);
      if (mounted) {
        mostrarMensagem(context,
            'Finalizada: ${_fmt(f.duracaoSegundos)} de duração, ${f.totalParticipantes} pessoa(s).');
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      _recarregar();
    }
  }

  Future<void> _cancelar(AtividadeResumo a) async {
    final motivo = await pedirMotivo(context, 'Cancelar atividade #${a.id}');
    if (motivo == null) return;
    try {
      await session.api.cancelar(a.id, motivo);
      if (mounted) mostrarMensagem(context, 'Atividade cancelada.');
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } finally {
      _recarregar();
    }
  }

  @override
  Widget build(BuildContext context) {
    final u = session.usuario;
    return Scaffold(
      appBar: AppBar(
        title: Text(u?.nome ?? 'Torre'),
        actions: [
          IconButton(
            tooltip: 'Sair',
            icon: const Icon(Icons.logout),
            onPressed: () => session.sairConta(),
          ),
        ],
        bottom: PreferredSize(
          preferredSize: const Size.fromHeight(20),
          child: Padding(
            padding: const EdgeInsets.only(bottom: 8, left: 16),
            child: Align(
              alignment: Alignment.centerLeft,
              child: Text('Filial ${u?.idFilial ?? '-'} · ${u?.perfil ?? ''}',
                  style: const TextStyle(color: Colors.white70, fontSize: 12)),
            ),
          ),
        ),
      ),
      body: RefreshIndicator(
        onRefresh: () async => _recarregar(),
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            _grade(),
            const SizedBox(height: 24),
            const Text('Atividades abertas',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            _listaAbertas(),
          ],
        ),
      ),
    );
  }

  Widget _grade() {
    final itens = <_Fluxo>[
      _Fluxo('Descarga\ntransferência', Icons.local_shipping, () => _abrir(const ViagensScreen())),
      _Fluxo('Descarga\ncoleta', Icons.move_to_inbox, () => _abrir(const ViagensColetaScreen())),
      _Fluxo('Separação', Icons.call_split, () => _abrir(const SeparacaoScreen())),
      _Fluxo('Carregamento', Icons.upload, () => _abrir(const CarregamentoScreen())),
      _Fluxo('Outras', Icons.handyman, () => _abrir(const OutrasScreen())),
      _Fluxo('Ocorrência', Icons.report_problem, () => _abrir(const OcorrenciaScreen())),
    ];
    return GridView.count(
      crossAxisCount: 2,
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      mainAxisSpacing: 12,
      crossAxisSpacing: 12,
      childAspectRatio: 1.4,
      children: itens
          .map((f) => Card(
                child: InkWell(
                  onTap: f.onTap,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(f.icone, size: 36, color: Colors.blue),
                      const SizedBox(height: 8),
                      Text(f.titulo, textAlign: TextAlign.center),
                    ],
                  ),
                ),
              ))
          .toList(),
    );
  }

  Widget _listaAbertas() {
    return FutureBuilder<List<AtividadeResumo>>(
      future: _abertas,
      builder: (context, snap) {
        if (snap.connectionState == ConnectionState.waiting) {
          return const Padding(
              padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator()));
        }
        if (snap.hasError) {
          return Padding(
              padding: const EdgeInsets.all(8), child: Text('Erro: ${snap.error}'));
        }
        final lista = snap.data ?? [];
        if (lista.isEmpty) {
          return const Padding(
              padding: EdgeInsets.all(8), child: Text('Nenhuma atividade aberta.'));
        }
        return Column(
          children: lista
              .map((a) => Card(
                    child: ListTile(
                      title: Text('${_rotuloTipo(a.tipo)}'
                          '${a.placaVeiculo != null ? ' · ${a.placaVeiculo}' : ''}'),
                      subtitle: Text('#${a.id} · ${a.participantesAtivos} ativo(s)'),
                      onTap: () => _reabrir(a),
                      trailing: PopupMenuButton<String>(
                        onSelected: (v) {
                          if (v == 'finalizar') _finalizar(a);
                          if (v == 'cancelar') _cancelar(a);
                          if (v == 'abrir') _reabrir(a);
                        },
                        itemBuilder: (_) => const [
                          PopupMenuItem(value: 'abrir', child: Text('Abrir')),
                          PopupMenuItem(value: 'finalizar', child: Text('Finalizar')),
                          PopupMenuItem(value: 'cancelar', child: Text('Cancelar')),
                        ],
                      ),
                    ),
                  ))
              .toList(),
        );
      },
    );
  }
}

class _Fluxo {
  final String titulo;
  final IconData icone;
  final VoidCallback onTap;
  _Fluxo(this.titulo, this.icone, this.onTap);
}

String _rotuloTipo(String tipo) {
  switch (tipo) {
    case 'DESCARGA_TRANSFERENCIA':
      return 'Descarga transferência';
    case 'DESCARGA_COLETA':
      return 'Descarga coleta';
    case 'SEPARACAO':
      return 'Separação';
    case 'CARREGAMENTO':
      return 'Carregamento';
    default:
      return 'Outras';
  }
}

String _fmt(int segundos) {
  final h = segundos ~/ 3600;
  final m = (segundos % 3600) ~/ 60;
  return h > 0 ? '${h}h ${m}min' : '${m}min';
}
