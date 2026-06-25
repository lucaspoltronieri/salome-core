import 'package:flutter/material.dart';

import '../../api/api_client.dart';
import '../../main.dart';
import '../../models/models.dart';
import '../../widgets/dialogos.dart';
import 'coleta_screen.dart';

/// Viagens trazendo coletas da própria filial (facilita abrir a descarga de coleta).
class ViagensColetaScreen extends StatefulWidget {
  const ViagensColetaScreen({super.key});

  @override
  State<ViagensColetaScreen> createState() => _ViagensColetaScreenState();
}

class _ViagensColetaScreenState extends State<ViagensColetaScreen> {
  late Future<List<ViagemAguardando>> _future;

  @override
  void initState() {
    super.initState();
    _recarregar();
  }

  void _recarregar() {
    setState(() => _future = session.api.coletasAguardando());
  }

  Future<void> _abrir(ViagemAguardando v, {required bool semViagem}) async {
    final titulo = semViagem
        ? 'Abrir uma descarga de coleta avulsa?'
        : 'Abrir a descarga de coleta da viagem ${v.placa ?? v.idViagem} (${v.qtdCtes} coletas)?';
    if (!await confirmar(context, 'Abrir descarga de coleta', titulo)) return;
    try {
      final atv = await session.api.abrirAtividade(
        tipo: 'DESCARGA_COLETA',
        idViagem: semViagem ? null : v.idViagem,
        placa: semViagem ? null : v.placa,
      );
      if (!mounted) return;
      await Navigator.push(
          context, MaterialPageRoute(builder: (_) => ColetaScreen(atividade: atv)));
      _recarregar();
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Coletas aguardando')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () => _abrir(
          ViagemAguardando(
              idViagem: null, idViagemTransferencia: 0, qtdCtes: 0, volumes: 0, peso: 0),
          semViagem: true,
        ),
        icon: const Icon(Icons.add),
        label: const Text('Coleta avulsa'),
      ),
      body: RefreshIndicator(
        onRefresh: () async => _recarregar(),
        child: FutureBuilder<List<ViagemAguardando>>(
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
                Padding(
                    padding: EdgeInsets.all(24),
                    child: Center(child: Text('Nenhuma viagem de coleta aguardando.')))
              ]);
            }
            return ListView.builder(
              padding: const EdgeInsets.all(12),
              itemCount: lista.length,
              itemBuilder: (_, i) {
                final v = lista[i];
                return Card(
                  child: ListTile(
                    leading: const Icon(Icons.move_to_inbox, color: Colors.teal),
                    title: Text(v.placa ?? 'Viagem ${v.idViagem}',
                        style: const TextStyle(fontWeight: FontWeight.bold)),
                    subtitle: Text([
                      if (v.motorista != null) v.motorista!,
                      '${v.qtdCtes} coletas · ${v.volumes.toStringAsFixed(0)} vol · ${v.peso.toStringAsFixed(0)} kg',
                    ].join('\n')),
                    isThreeLine: true,
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => _abrir(v, semViagem: false),
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
