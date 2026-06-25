import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api/api_client.dart';
import '../main.dart';
import '../widgets/dialogos.dart';

/// Registro de ocorrência (avaria, divergência, falta...) com foto opcional.
class OcorrenciaScreen extends StatefulWidget {
  const OcorrenciaScreen({super.key});

  @override
  State<OcorrenciaScreen> createState() => _OcorrenciaScreenState();
}

class _OcorrenciaScreenState extends State<OcorrenciaScreen> {
  static const _tipos = ['AVARIA', 'DIVERGENCIA', 'FALTA', 'EXCESSO', 'PENDENCIA', 'OUTRA'];

  String _tipo = _tipos.first;
  final _descricao = TextEditingController();
  final _placa = TextEditingController();
  String? _fotoPath;
  bool _enviando = false;

  @override
  void dispose() {
    _descricao.dispose();
    _placa.dispose();
    super.dispose();
  }

  Future<void> _tirarFoto() async {
    final x = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 70);
    if (x != null) setState(() => _fotoPath = x.path);
  }

  Future<void> _enviar() async {
    setState(() => _enviando = true);
    try {
      await session.api.registrarOcorrencia(
        tipo: _tipo,
        descricao: _descricao.text.trim().isEmpty ? null : _descricao.text.trim(),
        placa: _placa.text.trim().isEmpty ? null : _placa.text.trim(),
        fotoPath: _fotoPath,
      );
      if (mounted) {
        mostrarMensagem(context, 'Ocorrência registrada.');
        Navigator.pop(context);
      }
    } on ApiException catch (e) {
      if (mounted) mostrarMensagem(context, e.message, erro: true);
    } catch (_) {
      if (mounted) mostrarMensagem(context, 'Falha ao enviar a ocorrência.', erro: true);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Nova ocorrência')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          DropdownButtonFormField<String>(
            initialValue: _tipo,
            decoration: const InputDecoration(labelText: 'Tipo', border: OutlineInputBorder()),
            items: _tipos.map((t) => DropdownMenuItem(value: t, child: Text(t))).toList(),
            onChanged: (v) => setState(() => _tipo = v ?? _tipo),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _placa,
            decoration: const InputDecoration(
                labelText: 'Placa (opcional)', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 12),
          TextField(
            controller: _descricao,
            maxLines: 4,
            decoration: const InputDecoration(
                labelText: 'Descrição', border: OutlineInputBorder()),
          ),
          const SizedBox(height: 16),
          if (_fotoPath != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 12),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(8),
                child: Image.file(File(_fotoPath!), height: 180, fit: BoxFit.cover),
              ),
            ),
          OutlinedButton.icon(
            onPressed: _tirarFoto,
            icon: const Icon(Icons.camera_alt),
            label: Text(_fotoPath == null ? 'Tirar foto' : 'Trocar foto'),
          ),
          const SizedBox(height: 24),
          FilledButton(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: _enviando ? null : _enviar,
            child: _enviando
                ? const SizedBox(
                    height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Registrar ocorrência'),
          ),
        ],
      ),
    );
  }
}
