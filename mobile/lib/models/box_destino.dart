import 'models.dart';

/// Espelho do `BoxPadrao` do backend: os 3 boxes-destino e as regras por origem.
class BoxDestino {
  static const String sep = 'SEP';
  static const String dist = 'DIST';
  static const String transf = 'TRANSF';

  static const Map<String, String> rotulos = {
    sep: 'Separação',
    dist: 'Distribuição',
    transf: 'Transferência',
  };

  /// Códigos válidos (em ordem de exibição) conforme a origem da descarga.
  static List<String> codigosPara(OrigemDescarga origem) =>
      origem == OrigemDescarga.transferencia ? const [dist, sep] : const [transf, sep];

  /// Filtra/ordena os locais que são boxes-destino válidos para a origem.
  static List<LocalArmazem> boxesPara(List<LocalArmazem> locais, OrigemDescarga origem) {
    final cods = codigosPara(origem);
    final lista = locais.where((l) => cods.contains(l.codigo.toUpperCase())).toList();
    lista.sort((a, b) =>
        cods.indexOf(a.codigo.toUpperCase()).compareTo(cods.indexOf(b.codigo.toUpperCase())));
    return lista;
  }

  static String rotulo(LocalArmazem l) => rotulos[l.codigo.toUpperCase()] ?? l.nome;
}
