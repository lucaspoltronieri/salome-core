import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

/// Abre a câmera num bottom sheet e retorna o primeiro código lido (raw value),
/// ou null se o usuário fechar.
Future<String?> abrirScanner(BuildContext context, {String titulo = 'Bipar com a câmera'}) {
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
  final MobileScannerController _controller = MobileScannerController();
  bool _capturado = false;

  @override
  void initState() {
    super.initState();
    // mobile_scanner 5.x NÃO inicia a câmera sozinho: é preciso start() manual.
    // Sem isto a câmera fica preta (era a causa de "a câmera não funciona").
    unawaited(_controller.start());
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onDetect(BarcodeCapture capture) {
    if (_capturado) return;
    for (final b in capture.barcodes) {
      final raw = b.rawValue;
      if (raw != null && raw.isNotEmpty) {
        _capturado = true;
        Navigator.of(context).pop(raw);
        return;
      }
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
                icon: const Icon(Icons.flip_camera_ios),
                onPressed: () => _controller.switchCamera(),
              ),
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
          Expanded(
            child: MobileScanner(
              controller: _controller,
              onDetect: _onDetect,
              errorBuilder: (context, error, child) => Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const Icon(Icons.no_photography, size: 48, color: Colors.grey),
                    const SizedBox(height: 12),
                    Text(
                      'Não foi possível abrir a câmera.\n'
                      '${error.errorCode == MobileScannerErrorCode.permissionDenied ? 'Permissão de câmera negada — libere nas configurações do app.' : 'Use a digitação manual para continuar.'}',
                      textAlign: TextAlign.center,
                    ),
                  ],
                ),
              ),
            ),
          ),
          const Padding(
            padding: EdgeInsets.all(12),
            child: Text('Aponte para o código de barras do documento'),
          ),
        ],
      ),
    );
  }
}
