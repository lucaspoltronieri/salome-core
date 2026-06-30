import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

/// Abre a câmera num bottom sheet e retorna o primeiro código lido (raw value),
/// ou null se o usuário fechar. Se a câmera falhar (permissão negada, device sem
/// câmera, etc.) o operador ainda consegue digitar o código manualmente — o valor
/// digitado também volta por aqui, então o chamador não precisa de outro caminho.
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
  final MobileScannerController _controller = MobileScannerController(
    autoStart: false,
  );
  bool _capturado = false;
  bool _iniciando = false;
  // Mensagem de erro quando a câmera não sobe (mostra fallback de digitação).
  String? _erro;

  @override
  void initState() {
    super.initState();
    // mobile_scanner 5.x NÃO inicia a câmera sozinho: é preciso start() manual.
    // Iniciar dentro de initState() chega cedo demais em alguns aparelhos
    // (a platform view/superfície ainda não existe) e o start() falha calado,
    // deixando a câmera preta. Iniciamos no 1º frame e tratamos o erro.
    WidgetsBinding.instance.addPostFrameCallback((_) => _iniciarCamera());
  }

  Future<void> _iniciarCamera() async {
    if (!mounted || _iniciando) return;
    setState(() {
      _iniciando = true;
      _erro = null;
    });
    try {
      await _controller.start();
      if (mounted) setState(() => _erro = null);
    } catch (e) {
      if (mounted) setState(() => _erro = _mensagemErro(e));
    } finally {
      if (mounted) setState(() => _iniciando = false);
    }
  }

  String _mensagemErro(Object e) {
    if (e is MobileScannerException &&
        e.errorCode == MobileScannerErrorCode.permissionDenied) {
      return 'Permissão de câmera negada — libere nas configurações do aparelho '
          'e tente de novo, ou digite o código manualmente.';
    }
    return 'Não foi possível abrir a câmera. Digite o código manualmente.';
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
                icon: const Icon(Icons.flip_camera_ios),
                onPressed: (_erro == null && !_iniciando)
                    ? () => _controller.switchCamera()
                    : null,
              ),
              IconButton(
                icon: const Icon(Icons.close),
                onPressed: () => Navigator.of(context).pop(),
              ),
            ],
          ),
          Expanded(
            child: Stack(
              fit: StackFit.expand,
              children: [
                MobileScanner(
                  controller: _controller,
                  onDetect: _onDetect,
                  errorBuilder: (context, error, child) => _ErroCamera(
                    mensagem: _mensagemErro(error),
                    aoDigitar: _digitarManual,
                    aoTentar: _iniciarCamera,
                  ),
                ),
                if (_erro != null)
                  ColoredBox(
                    color: Theme.of(context).scaffoldBackgroundColor,
                    child: _ErroCamera(
                      mensagem: _erro!,
                      aoDigitar: _digitarManual,
                      aoTentar: _iniciarCamera,
                    ),
                  )
                else if (_iniciando)
                  const ColoredBox(
                    color: Colors.black,
                    child: Center(child: CircularProgressIndicator()),
                  ),
              ],
            ),
          ),
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
}

class _ErroCamera extends StatelessWidget {
  final String mensagem;
  final VoidCallback aoDigitar;
  final Future<void> Function() aoTentar;
  const _ErroCamera({
    required this.mensagem,
    required this.aoDigitar,
    required this.aoTentar,
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
