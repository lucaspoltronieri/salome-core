import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../models/models.dart';
import 'cronometro.dart';
import 'dialogos.dart';

/// Botão "Pessoas" que abre o painel de quem está na atividade (com o tempo de
/// cada um rodando) e permite adicionar/remover colegas e chapas.
class BotaoPessoas extends StatelessWidget {
  final int idAtividade;
  final int ativos;
  const BotaoPessoas({super.key, required this.idAtividade, required this.ativos});

  @override
  Widget build(BuildContext context) {
    return TextButton.icon(
      icon: const Icon(Icons.groups, color: Colors.white),
      label: Text('$ativos', style: const TextStyle(color: Colors.white)),
      onPressed: () => mostrarPessoas(context, idAtividade),
    );
  }
}

Future<void> mostrarPessoas(BuildContext context, int idAtividade) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _PainelPessoas(idAtividade: idAtividade),
  );
}

class _PainelPessoas extends StatefulWidget {
  final int idAtividade;
  const _PainelPessoas({required this.idAtividade});

  @override
  State<_PainelPessoas> createState() => _PainelPessoasState();
}

class _PainelPessoasState extends State<_PainelPessoas> {
  AtividadeResumo? _atv;
  bool _carregando = true;

  @override
  void initState() {
    super.initState();
    _recarregar();
  }

  Future<void> _recarregar() async {
    try {
      final a = await session.api.buscarAtividade(widget.idAtividade);
      if (mounted) setState(() {
        _atv = a;
        _carregando = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _carregando = false);
        mostrarMensagem(context, e.message, erro: true);
      }
    }
  }

  Future<void> _remover(Participante p) async {
    if (p.idUsuario == null) return;
    try {
      final a = await session.api.removerParticipante(widget.idAtividade, p.idUsuario!);
      if (mounted) setState(() => _atv = a);
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _adicionar() async {
    final ativosIds = (_atv?.participantes ?? [])
        .where((p) => p.ativo)
        .map((p) => p.idUsuario)
        .whereType<int>()
        .toSet();
    final escolhido = await showModalBottomSheet<Usuario>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _SeletorUsuario(jaNaAtividade: ativosIds),
    );
    if (escolhido == null) return;
    try {
      final a = await session.api.adicionarParticipante(widget.idAtividade, escolhido.id);
      if (mounted) {
        setState(() => _atv = a);
        mostrarMensagem(context, '${escolhido.nome} entrou na atividade.');
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  Future<void> _adicionarChapa() async {
    final ctrl = TextEditingController();
    final nome = await showDialog<String>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Chapa avulsa'),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          textCapitalization: TextCapitalization.words,
          decoration: const InputDecoration(hintText: 'Nome da pessoa'),
          onSubmitted: (v) => Navigator.of(ctx).pop(v.trim()),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Cancelar')),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(ctrl.text.trim()),
            child: const Text('Adicionar'),
          ),
        ],
      ),
    );
    if (nome == null || nome.isEmpty) return;
    try {
      final a = await session.api.adicionarChapa(widget.idAtividade, nome);
      if (mounted) {
        setState(() => _atv = a);
        mostrarMensagem(context, '$nome entrou como chapa.');
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    }
  }

  @override
  Widget build(BuildContext context) {
    final ativos = (_atv?.participantes ?? []).where((p) => p.ativo).toList();
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Row(
              children: [
                const Text('Pessoas na atividade',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                const Spacer(),
                Text('${ativos.length} ativo(s)', style: const TextStyle(color: Colors.grey)),
              ],
            ),
            const SizedBox(height: 8),
            if (_carregando)
              const Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()))
            else if (ativos.isEmpty)
              const Padding(padding: EdgeInsets.all(16), child: Text('Ninguém ativo na atividade.'))
            else
              ...ativos.map((p) => ListTile(
                    dense: true,
                    leading: const Icon(Icons.person, color: Colors.blue),
                    title: Text(p.nomeUsuario ?? 'Pessoa #${p.idUsuario}'),
                    subtitle: Cronometro(
                      inicio: Cronometro.parse(p.entradaEm),
                      estilo: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
                    ),
                    trailing: IconButton(
                      icon: const Icon(Icons.person_remove, color: Colors.red),
                      tooltip: 'Remover',
                      onPressed: () => _remover(p),
                    ),
                  )),
            const SizedBox(height: 8),
            FilledButton.icon(
              icon: const Icon(Icons.person_add),
              label: const Text('Adicionar pessoa'),
              onPressed: _adicionar,
            ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              icon: const Icon(Icons.engineering),
              label: const Text('Chapa avulsa (digitar nome)'),
              onPressed: _adicionarChapa,
            ),
          ],
        ),
      ),
    );
  }
}

/// Seletor de usuário da filial (com busca), excluindo quem já está na atividade.
class _SeletorUsuario extends StatefulWidget {
  final Set<int> jaNaAtividade;
  const _SeletorUsuario({required this.jaNaAtividade});

  @override
  State<_SeletorUsuario> createState() => _SeletorUsuarioState();
}

class _SeletorUsuarioState extends State<_SeletorUsuario> {
  List<Usuario> _todos = [];
  String _filtro = '';
  bool _carregando = true;

  @override
  void initState() {
    super.initState();
    _carregar();
  }

  Future<void> _carregar() async {
    try {
      final us = await session.api.listarUsuarios();
      if (mounted) setState(() {
        _todos = us.where((u) => !widget.jaNaAtividade.contains(u.id)).toList();
        _carregando = false;
      });
    } on ApiException catch (e) {
      if (mounted) {
        setState(() => _carregando = false);
        mostrarMensagem(context, e.message, erro: true);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final f = _filtro.toLowerCase();
    final visiveis = _todos.where((u) => u.nome.toLowerCase().contains(f)).toList();
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
            left: 16, right: 16, top: 16, bottom: MediaQuery.of(context).viewInsets.bottom + 16),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Text('Quem entra na atividade', style: TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            TextField(
              autofocus: true,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                hintText: 'Buscar pessoa...',
                border: OutlineInputBorder(),
                isDense: true,
              ),
              onChanged: (v) => setState(() => _filtro = v),
            ),
            const SizedBox(height: 8),
            if (_carregando)
              const Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator())
            else
              SizedBox(
                height: 320,
                child: ListView(
                  children: visiveis
                      .map((u) => ListTile(
                            leading: Icon(u.perfil == 'CHAPA' ? Icons.engineering : Icons.person),
                            title: Text(u.nome),
                            subtitle: Text(u.perfil == 'CHAPA' ? 'Chapa' : u.login),
                            onTap: () => Navigator.pop(context, u),
                          ))
                      .toList(),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
