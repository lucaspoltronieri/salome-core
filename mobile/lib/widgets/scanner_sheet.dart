import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_zxing/flutter_zxing.dart';
import 'package:permission_handler/permission_handler.dart';

/// Abre a câmera num bottom sheet e retorna o primeiro código lido (raw value),
/// ou null se o usuário fechar. Se a câmera falhar (permissão negada, device sem
/// câmera, etc.) o operador ainda consegue digitar o código manualmente — o valor
/// digitado também volta por aqui, então o chamador não precisa de outro caminho.
///
/// Motor de leitura: **ZXing** (via flutter_zxing) — usa o plugin `camera` (camera2)
/// e não depende de ML Kit / CameraX, que falhavam no `start()` em alguns aparelhos.
Future<String?> abrirScanner(BuildContext context,
    {String titulo = 'Bipar com a câmera'}) {
  return showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    builder: (_) => _ScannerSheet(titulo: titulo),
  );
}

class _ScannerSheet extends StatefulWidget {
  final String titulo;
  const _ScannerSheet({required this.titulo});

  @override
  State<_ScannerSheet> createState() => _ScannerSheetState();
}

class _ScannerSheetState extends State<_ScannerSheet> {
  bool _capturado = false;
  bool _verificando = true; // ainda checando a permissão de câmera
  bool _permitido = false;
  bool _negadaPermanente = false;

  @override
  void initState() {
    super.initState();
    _pedirPermissao();
  }

  /// Android 6+ exige permissão de câmera em runtime — o ReaderWidget só sobe depois de OK.
  Future<void> _pedirPermissao() async {
    if (!mounted) return;
    setState(() => _verificando = true);
    final status = await Permission.camera.request();
    if (!mounted) return;
    setState(() {
      _verificando = false;
      _permitido = status.isGranted;
      _negadaPermanente = status.isPermanentlyDenied;
    });
  }

  void _onScan(Code code) {
    if (_capturado) return;
    final txt = code.text;
    if (code.isValid && txt != null && txt.isNotEmpty) {
      _capturado = true;
      Navigator.of(context).pop(txt);
    }
  }

  void _onMultiScan(Codes codes) {
    if (_capturado) return;
    String? fallback;
    for (final code in codes.codes) {
      final txt = code.text;
      if (code.isValid && txt != null && txt.isNotEmpty) {
        fallback ??= txt;
        final chave = RegExp(r'(?:^|\D)\d{44}(?:\D|$)').hasMatch(txt);
        if (chave) {
          _capturado = true;
          Navigator.of(context).pop(txt);
          return;
        }
      }
    }
    if (fallback != null) {
      _capturado = true;
      Navigator.of(context).pop(fallback);
    }
  }

  Future<void> _digitarManual() async {
    final ctrl = TextEditingController();
    try {
      final valor = await showDialog<String>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: const Text('Digitar código'),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            keyboardType: TextInputType.text,
            decoration:
                const InputDecoration(hintText: 'Chave / número do documento'),
            onSubmitted: (v) => Navigator.of(ctx).pop(v.trim()),
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: const Text('Cancelar')),
            ElevatedButton(
              onPressed: () => Navigator.of(ctx).pop(ctrl.text.trim()),
              child: const Text('Confirmar'),
            ),
          ],
        ),
      );
      if (!mounted) return;
      if (valor != null && valor.isNotEmpty) {
        _capturado = true;
        Navigator.of(context).pop(valor);
      }
    } finally {
      ctrl.dispose();
    }
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: MediaQuery.of(context).size.height * 0.6,
      child: Column(
        children: [
          AppBar(
            title: Text(widget.titulo),
            automaticallyImplyLeading: false,
            actions: [
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
          Expanded(child: _corpo()),
          Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Expanded(
                    child: Text('Aponte para o código de barras do documento')),
                TextButton.icon(
                  onPressed: _digitarManual,
                  icon: const Icon(Icons.keyboard),
                  label: const Text('Digitar'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _corpo() {
    if (_verificando) {
      return const ColoredBox(
        color: Colors.black,
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (!_permitido) {
      return ColoredBox(
        color: Theme.of(context).scaffoldBackgroundColor,
        child: _ErroCamera(
          mensagem: _negadaPermanente
              ? 'Permissão de câmera negada. Toque em "Abrir configurações" para '
                  'liberar, ou digite o código manualmente.'
              : 'Precisamos da câmera pra bipar. Toque em "Tentar de novo" e permita '
                  'o acesso, ou digite o código manualmente.',
          aoDigitar: _digitarManual,
          aoTentar: _pedirPermissao,
          aoAbrirConfig: _negadaPermanente ? () => openAppSettings() : null,
        ),
      );
    }
    // Multi-scan evita perder a leitura quando QR e código de barras aparecem juntos no documento.
    return ReaderWidget(
      onScan: _onScan,
      onMultiScan: _onMultiScan,
      isMultiScan: true,
      codeFormat: Format.any,
      tryHarder: true,
      tryInverted: true,
      tryDownscale: true,
      scanDelay: const Duration(milliseconds: 250),
      scanDelaySuccess: const Duration(milliseconds: 300),
    );
  }
}

class _ErroCamera extends StatelessWidget {
  final String mensagem;
  final VoidCallback aoDigitar;
  final Future<void> Function() aoTentar;
  /// Só preenchido quando a permissão foi negada "para sempre": abre as configurações do app.
  final VoidCallback? aoAbrirConfig;
  const _ErroCamera({
    required this.mensagem,
    required this.aoDigitar,
    required this.aoTentar,
    this.aoAbrirConfig,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.no_photography, size: 48, color: Colors.grey),
          const SizedBox(height: 12),
          Text(mensagem, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          Wrap(
            spacing: 8,
            children: [
              if (aoAbrirConfig != null)
                OutlinedButton.icon(
                  onPressed: aoAbrirConfig,
                  icon: const Icon(Icons.settings),
                  label: const Text('Abrir configurações'),
                )
              else
                OutlinedButton.icon(
                  onPressed: () => aoTentar(),
                  icon: const Icon(Icons.refresh),
                  label: const Text('Tentar de novo'),
                ),
              ElevatedButton.icon(
                onPressed: aoDigitar,
                icon: const Icon(Icons.keyboard),
                label: const Text('Digitar manualmente'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
