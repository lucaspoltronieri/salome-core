import 'dart:async';

import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import 'package:permission_handler/permission_handler.dart';

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
  // Permissão negada "para sempre" → oferece abrir as configurações do app.
  bool _negadaPermanente = false;

  @override
  void initState() {
    super.initState();
    // mobile_scanner 5.x NÃO inicia a câmera sozinho: é preciso start() manual.
    // Iniciar cedo demais (no 1º frame, com o bottom sheet ainda subindo) pega a
    // platform view sem composição: o start() resolve "ok" mas não chegam frames
    // e o preview fica preto. Por isso esperamos a animação do modal terminar.
    WidgetsBinding.instance.addPostFrameCallback((_) => _agendarInicio());
  }

  /// Só liga a câmera depois que o bottom sheet terminou de subir (evita preview preto).
  void _agendarInicio() {
    if (!mounted) return;
    final anim = ModalRoute.of(context)?.animation;
    if (anim == null || anim.status == AnimationStatus.completed) {
      _iniciarCamera();
      return;
    }
    void ouvinte(AnimationStatus s) {
      if (s == AnimationStatus.completed || s == AnimationStatus.dismissed) {
        anim.removeStatusListener(ouvinte);
        if (s == AnimationStatus.completed) _iniciarCamera();
      }
    }

    anim.addStatusListener(ouvinte);
  }

  Future<void> _iniciarCamera({bool retentar = true}) async {
    if (!mounted || _iniciando) return;
    setState(() {
      _iniciando = true;
      _erro = null;
      _negadaPermanente = false;
    });
    // Android 6+ exige pedir a permissão de câmera em runtime — sem isso o start()
    // falha com erro genérico e a câmera nunca abre.
    final permissao = await Permission.camera.request();
    if (!mounted) return;
    if (!permissao.isGranted) {
      setState(() {
        _iniciando = false;
        _negadaPermanente = permissao.isPermanentlyDenied;
        _erro = permissao.isPermanentlyDenied
            ? 'Permissão de câmera negada. Toque em "Abrir configurações" para liberar, '
                'ou digite o código manualmente.'
            : 'Precisamos da câmera pra bipar. Toque em "Tentar de novo" e permita o acesso, '
                'ou digite o código manualmente.';
      });
      return;
    }
    Object? erro;
    try {
      await _controller.start();
    } catch (e) {
      erro = e;
    }
    if (!mounted) return;
    if (erro == null) {
      setState(() {
        _iniciando = false;
        _erro = null;
      });
      return;
    }
    // Uma re-tentativa após curto intervalo cobre a superfície ainda não pronta;
    // persistindo, mostra o fallback de digitação.
    if (retentar) {
      setState(() => _iniciando = false);
      await Future.delayed(const Duration(milliseconds: 300));
      return _iniciarCamera(retentar: false);
    }
    setState(() {
      _iniciando = false;
      _erro = _mensagemErro(erro!); // não-nulo: early-return acima cobre erro == null
    });
  }

  String _mensagemErro(Object e) {
    if (e is MobileScannerException &&
        e.errorCode == MobileScannerErrorCode.permissionDenied) {
      return 'Permissão de câmera negada — libere nas configurações do aparelho '
          'e tente de novo, ou digite o código manualmente.';
    }
    // Diagnóstico: expõe o erro real da câmera pra identificar a causa (a permissão já
    // está concedida, então start() falha por outro motivo).
    final det = e is MobileScannerException
        ? 'code=${e.errorCode}; ${e.errorDetails?.message ?? ''}'
        : e.toString();
    return 'Não foi possível abrir a câmera. Digite o código manualmente.\n\n[$det]';
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
                  // v7: errorBuilder passou a receber apenas (context, error).
                  errorBuilder: (context, error) => _ErroCamera(
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
                      aoAbrirConfig: _negadaPermanente ? () => openAppSettings() : null,
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
