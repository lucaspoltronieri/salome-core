import '../models/models.dart';
import 'api_client.dart';

/// Um método por endpoint do backend `/api/torre/*`.
class TorreApi {
  final ApiClient _c;
  TorreApi(this._c);

  ApiClient get client => _c;

  // ---- Auth -----------------------------------------------------------
  Future<LoginResult> login(String login, String senha) async {
    final j = await _c.post('/api/torre/auth/login', body: {'login': login, 'senha': senha});
    return LoginResult.fromJson(j as Map<String, dynamic>);
  }

  // ---- Viagens (legado) ----------------------------------------------
  Future<List<ViagemAguardando>> viagensAguardando() async {
    final j = await _c.get('/api/torre/viagens/aguardando-descarga');
    return (j as List).map((e) => ViagemAguardando.fromJson(e)).toList();
  }

  Future<List<ViagemAguardando>> coletasAguardando() async {
    final j = await _c.get('/api/torre/viagens/coletas-aguardando');
    return (j as List).map((e) => ViagemAguardando.fromJson(e)).toList();
  }

  // ---- Atividade ------------------------------------------------------
  Future<AtividadeResumo> abrirAtividade({
    required String tipo,
    String? subtipo,
    int? idViagem,
    String? placa,
    String? funcao,
  }) async {
    final j = await _c.post('/api/torre/atividades', body: {
      'tipo': tipo,
      if (subtipo != null) 'subtipo': subtipo,
      if (idViagem != null) 'idViagem': idViagem,
      if (placa != null) 'placa': placa,
      if (funcao != null) 'funcao': funcao,
    });
    return AtividadeResumo.fromJson(j as Map<String, dynamic>);
  }

  Future<List<AtividadeResumo>> atividadesAbertas() async {
    final j = await _c.get('/api/torre/atividades/abertas');
    return (j as List).map((e) => AtividadeResumo.fromJson(e)).toList();
  }

  Future<AtividadeResumo> entrar(int idAtividade, {String? funcao}) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/entrar',
        body: funcao == null ? {} : {'funcao': funcao});
    return AtividadeResumo.fromJson(j as Map<String, dynamic>);
  }

  Future<AtividadeResumo> sair(int idAtividade) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/sair');
    return AtividadeResumo.fromJson(j as Map<String, dynamic>);
  }

  Future<AtividadeFinalizada> finalizar(int idAtividade) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/finalizar');
    return AtividadeFinalizada.fromJson(j as Map<String, dynamic>);
  }

  Future<void> cancelar(int idAtividade, String motivo) async {
    await _c.post('/api/torre/atividades/$idAtividade/cancelar', body: {'motivo': motivo});
  }

  // ---- Descarga transferência ----------------------------------------
  Future<List<CteDescarga>> ctesDisponiveis(int idAtividade) async {
    final j = await _c.get('/api/torre/atividades/$idAtividade/ctes-disponiveis');
    return (j as List).map((e) => CteDescarga.fromJson(e)).toList();
  }

  Future<List<DocumentoOperacional>> documentosDaAtividade(int idAtividade) async {
    final j = await _c.get('/api/torre/atividades/$idAtividade/documentos');
    return (j as List).map((e) => DocumentoOperacional.fromJson(e)).toList();
  }

  Future<DocumentoOperacional> registrarDescarga(
      int idAtividade, int idConhecimento, int idLocalDestino) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/documentos',
        body: {'idConhecimento': idConhecimento, 'idLocalDestino': idLocalDestino});
    return DocumentoOperacional.fromJson(j as Map<String, dynamic>);
  }

  // ---- Descarga coleta ------------------------------------------------
  Future<DocumentoOperacional> biparNf(
    int idAtividade, {
    required String chaveNf,
    String? numeroNf,
    String? serie,
    String? cnpjEmitente,
    int? volumes,
    double? peso,
    required int idLocalDestino,
  }) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/coletas', body: {
      'chaveNf': chaveNf,
      if (numeroNf != null) 'numeroNf': numeroNf,
      if (serie != null) 'serie': serie,
      if (cnpjEmitente != null) 'cnpjEmitente': cnpjEmitente,
      if (volumes != null) 'volumes': volumes,
      if (peso != null) 'peso': peso,
      'idLocalDestino': idLocalDestino,
    });
    return DocumentoOperacional.fromJson(j as Map<String, dynamic>);
  }

  // ---- Separação / Carregamento --------------------------------------
  Future<List<DocumentoOperacional>> disponiveis(String para) async {
    final j = await _c.get('/api/torre/documentos/disponiveis', query: {'para': para});
    return (j as List).map((e) => DocumentoOperacional.fromJson(e)).toList();
  }

  Future<DocumentoOperacional> separar(int idAtividade, int idDocumento, int idLocal) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/separar',
        body: {'idDocumento': idDocumento, 'idLocal': idLocal});
    return DocumentoOperacional.fromJson(j as Map<String, dynamic>);
  }

  Future<DocumentoOperacional> carregar(int idAtividade, int idDocumento) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/carregar',
        body: {'idDocumento': idDocumento});
    return DocumentoOperacional.fromJson(j as Map<String, dynamic>);
  }

  // ---- Locais ---------------------------------------------------------
  Future<List<LocalArmazem>> locais() async {
    final j = await _c.get('/api/torre/locais');
    return (j as List).map((e) => LocalArmazem.fromJson(e)).toList();
  }

  // ---- Ocorrência -----------------------------------------------------
  Future<void> registrarOcorrencia({
    required String tipo,
    String? descricao,
    int? idDocumento,
    int? idAtividade,
    String? placa,
    String? fotoPath,
  }) async {
    final dados = {
      'tipo': tipo,
      if (descricao != null) 'descricao': descricao,
      if (idDocumento != null) 'idDocumento': idDocumento,
      if (idAtividade != null) 'idAtividade': idAtividade,
      if (placa != null) 'placa': placa,
    };
    if (fotoPath != null) {
      await _c.postMultipart('/api/torre/ocorrencias', dados: dados, fotoPath: fotoPath);
    } else {
      await _c.post('/api/torre/ocorrencias', body: dados);
    }
  }
}
