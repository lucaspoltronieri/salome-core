String fmtDataBr(String? data) {
  if (data == null || data.isEmpty) return '';
  final partes = data.split('-');
  if (partes.length == 3 && partes[0].length == 4) {
    return '${partes[2].padLeft(2, '0')}/${partes[1].padLeft(2, '0')}/${partes[0]}';
  }
  return data;
}

String fmtDataHoraBr(String? data, String? hora) {
  final dataBr = fmtDataBr(data);
  final horaBr = hora ?? '';
  return [dataBr, horaBr].where((v) => v.isNotEmpty).join(' ');
}
