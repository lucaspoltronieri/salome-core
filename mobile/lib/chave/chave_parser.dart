/// Extrai dados da chave de 44 dígitos da NFe/CTe (DACTE).
///
/// Layout (1-based): cUF[1-2] AAMM[3-6] CNPJ[7-20] mod[21-22] serie[23-25]
/// numero[26-34] tpEmis[35] codigo[36-43] dv[44].
class ChaveParser {
  static final RegExp _so44 = RegExp(r'^\d{44}$');

  /// Normaliza o que veio do scanner: tira tudo que não é dígito.
  static String? normalizar(String? bruto) {
    if (bruto == null) return null;
    final digitos = bruto.replaceAll(RegExp(r'\D'), '');
    return _so44.hasMatch(digitos) ? digitos : null;
  }

  static bool valida(String? s) => s != null && _so44.hasMatch(s);

  /// Número do documento (nº do CT-e na transferência; nº da NF na coleta).
  static int? numero(String s) => valida(s) ? int.tryParse(s.substring(25, 34)) : null;

  static String? serie(String s) => valida(s) ? s.substring(22, 25) : null;

  static String? cnpjEmitente(String s) => valida(s) ? s.substring(6, 20) : null;
}
