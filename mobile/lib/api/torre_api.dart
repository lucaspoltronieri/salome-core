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

  Future<AtividadeResumo> buscarAtividade(int idAtividade) async {
    final j = await _c.get('/api/torre/atividades/$idAtividade');
    return AtividadeResumo.fromJson(j as Map<String, dynamic>);
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

  /// Conclui a atividade: efetiva o status final dos documentos e finaliza.
  Future<AtividadeFinalizada> concluir(int idAtividade) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/concluir');
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

  /// Modo rápido: marca vários CT-es de uma vez para o mesmo box destino.
  Future<List<DocumentoOperacional>> registrarDescargaLote(
      int idAtividade, List<int> idsConhecimento, int idLocalDestino) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/documentos/lote',
        body: {'idsConhecimento': idsConhecimento, 'idLocalDestino': idLocalDestino});
    return (j as List).map((e) => DocumentoOperacional.fromJson(e)).toList();
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

  /// Modo rápido: separa vários documentos de uma vez para o mesmo box (ex.: Distribuição).
  Future<List<DocumentoOperacional>> separarLote(
      int idAtividade, List<int> idsDocumento, int idLocal) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/separar/lote',
        body: {'idsDocumento': idsDocumento, 'idLocal': idLocal});
    return (j as List).map((e) => DocumentoOperacional.fromJson(e)).toList();
  }

  Future<DocumentoOperacional> carregar(int idAtividade, int idDocumento) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/carregar',
        body: {'idDocumento': idDocumento});
    return DocumentoOperacional.fromJson(j as Map<String, dynamic>);
  }

  /// Modo rápido: marca vários documentos para carregamento de uma vez.
  Future<List<DocumentoOperacional>> carregarLote(int idAtividade, List<int> idsDocumento) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/carregar/lote',
        body: {'idsDocumento': idsDocumento});
    return (j as List).map((e) => DocumentoOperacional.fromJson(e)).toList();
  }

  // ---- Pessoas na atividade ------------------------------------------
  /// Usuários da filial (para escolher quem adicionar à atividade).
  Future<List<Usuario>> listarUsuarios() async {
    final j = await _c.get('/api/torre/usuarios');
    return (j as List).map((e) => Usuario.fromJson(e)).toList();
  }

  Future<AtividadeResumo> adicionarParticipante(int idAtividade, int idUsuario, {String? funcao}) async {
    final j = await _c.post('/api/torre/atividades/$idAtividade/participantes',
        body: {'idUsuario': idUsuario, if (funcao != null) 'funcao': funcao});
    return AtividadeResumo.fromJson(j as Map<String, dynamic>);
  }

  Future<AtividadeResumo> removerParticipante(int idAtividade, int idUsuario) async {
    final j = await _c.delete('/api/torre/atividades/$idAtividade/participantes/$idUsuario');
    return AtividadeResumo.fromJson(j as Map<String, dynamic>);
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
