/// Configuração fixada no build. A URL do servidor da Torre é injetada via
/// `--dart-define=TORRE_BASE_URL=http://<ip>:8789` (ver build-apk.ps1).
class Config {
  /// Base do backend, sem barra final. Default aponta pro emulador local.
  static const String baseUrl = String.fromEnvironment(
    'TORRE_BASE_URL',
    defaultValue: 'http://10.0.2.2:8789',
  );
}
