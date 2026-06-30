import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import 'dialogos.dart';

/// Abre a seleção de placa (Entrega/Transferência) e devolve a placa escolhida
/// (existente ou recém-cadastrada), ou null se o operador voltar.
Future<String?> escolherPlaca(BuildContext context, String tipo, {String? titulo}) {
  return Navigator.push<String>(
    context,
    MaterialPageRoute(builder: (_) => SeletorPlacaScreen(tipo: tipo, titulo: titulo)),
  );
}

class SeletorPlacaScreen extends StatefulWidget {
  final String tipo; // ENTREGA | TRANSFERENCIA
  final String? titulo;
  const SeletorPlacaScreen({super.key, required this.tipo, this.titulo});

  @override
  State<SeletorPlacaScreen> createState() => _SeletorPlacaScreenState();
}

class _SeletorPlacaScreenState extends State<SeletorPlacaScreen> {
  List<Veiculo> _veiculos = [];
  bool _carregando = true;
  bool _ocupado = false;
  String? _erro;
  final _ctrl = TextEditingController();

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _carregar() async {
    setState(() => _carregando = true);
    try {
      final v = await session.api.veiculos(widget.tipo);
      if (mounted) {
        setState(() {
          _veiculos = v;
          _erro = null;
          _carregando = false;
        });
      }
    } on ApiException catch (e) {
      if (mounted) {
        setState(() {
          _erro = e.message;
          _carregando = false;
        });
      }
    }
  }

  Future<void> _cadastrar() async {
    final placa = _ctrl.text.trim().toUpperCase();
    if (placa.isEmpty) return;
    setState(() => _ocupado = true);
    try {
      final v = await session.api.cadastrarVeiculo(placa, widget.tipo);
      if (mounted) Navigator.pop(context, v.placa);
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _ocupado = false);
        mostrarMensagem(context, e.message, erro: true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(widget.titulo ?? 'Escolha a placa')),
      body: _carregando
          ? const Center(child: CircularProgressIndicator())
          : Column(
              children: [
                Padding(
                  padding: const EdgeInsets.all(12),
                  child: Row(
                    children: [
                      Expanded(
                        child: TextField(
                          controller: _ctrl,
                          textCapitalization: TextCapitalization.characters,
                          decoration: const InputDecoration(
                            prefixIcon: Icon(Icons.local_shipping),
                            labelText: 'Cadastrar nova placa',
                            border: OutlineInputBorder(),
                            isDense: true,
                          ),
                          onSubmitted: (_) => _ocupado ? null : _cadastrar(),
                        ),
                      ),
                      const SizedBox(width: 8),
                      FilledButton(
                        onPressed: _ocupado ? null : _cadastrar,
                        child: const Text('Usar'),
                      ),
                    ],
                  ),
                ),
                if (_erro != null)
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(_erro!, style: const TextStyle(color: Colors.red)),
                  ),
                Expanded(
                  child: RefreshIndicator(
                    onRefresh: _carregar,
                    child: _veiculos.isEmpty
                        ? ListView(children: const [
                            Padding(
                              padding: EdgeInsets.all(24),
                              child: Center(child: Text('Nenhuma placa cadastrada. Digite acima para cadastrar.')),
                            )
                          ])
                        : ListView.separated(
                            itemCount: _veiculos.length,
                            separatorBuilder: (_, __) => const Divider(height: 1),
                            itemBuilder: (_, i) {
                              final v = _veiculos[i];
                              return ListTile(
                                leading: const Icon(Icons.local_shipping, color: Colors.blue),
                                title: Text(v.placa,
                                    style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                                trailing: const Icon(Icons.chevron_right),
                                onTap: () => Navigator.pop(context, v.placa),
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
