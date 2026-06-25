// Modelos espelhando os records Java do backend (mesmos nomes de campo no JSON).

int? _asInt(dynamic v) => v == null ? null : (v as num).toInt();
double? _asDouble(dynamic v) => v == null ? null : (v as num).toDouble();

class Usuario {
  final int id;
  final String nome;
  final String login;
  final int idFilial;
  final String perfil;

  Usuario({
    required this.id,
    required this.nome,
    required this.login,
    required this.idFilial,
    required this.perfil,
  });

  bool get isAdmin => perfil == 'ADMIN';

  factory Usuario.fromJson(Map<String, dynamic> j) => Usuario(
        id: (j['id'] as num).toInt(),
        nome: j['nome'] ?? '',
        login: j['login'] ?? '',
        idFilial: (j['idFilial'] as num).toInt(),
        perfil: j['perfil'] ?? 'OPERADOR',
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'nome': nome,
        'login': login,
        'idFilial': idFilial,
        'perfil': perfil,
      };
}

class LoginResult {
  final String token;
  final String? expiraEm;
  final Usuario usuario;

  LoginResult({required this.token, this.expiraEm, required this.usuario});

  factory LoginResult.fromJson(Map<String, dynamic> j) => LoginResult(
        token: j['token'],
        expiraEm: j['expiraEm']?.toString(),
        usuario: Usuario.fromJson(j['usuario']),
      );
}

/// Origem de uma descarga, define os destinos válidos.
enum OrigemDescarga { transferencia, coleta }

class ViagemAguardando {
  final int? idViagem;
  final int idViagemTransferencia;
  final String? placa;
  final String? motorista;
  final String? origem;
  final String? dataBaixa;
  final String? horaBaixa;
  final int qtdCtes;
  final double volumes;
  final double peso;

  ViagemAguardando({
    required this.idViagem,
    required this.idViagemTransferencia,
    this.placa,
    this.motorista,
    this.origem,
    this.dataBaixa,
    this.horaBaixa,
    required this.qtdCtes,
    required this.volumes,
    required this.peso,
  });

  factory ViagemAguardando.fromJson(Map<String, dynamic> j) => ViagemAguardando(
        idViagem: _asInt(j['idViagem']),
        idViagemTransferencia: (j['idViagemTransferencia'] as num).toInt(),
        placa: j['placa'],
        motorista: j['motorista'],
        origem: j['origem'],
        dataBaixa: j['dataBaixa']?.toString(),
        horaBaixa: j['horaBaixa'],
        qtdCtes: _asInt(j['qtdCtes']) ?? 0,
        volumes: _asDouble(j['volumes']) ?? 0,
        peso: _asDouble(j['peso']) ?? 0,
      );
}

class Participante {
  final int? id;
  final String? nomeUsuario;
  final String? funcao;
  final String? entradaEm;
  final String? saidaEm;

  Participante({this.id, this.nomeUsuario, this.funcao, this.entradaEm, this.saidaEm});

  bool get ativo => saidaEm == null;

  factory Participante.fromJson(Map<String, dynamic> j) => Participante(
        id: _asInt(j['id']),
        nomeUsuario: j['nomeUsuario'],
        funcao: j['funcao'],
        entradaEm: j['entradaEm']?.toString(),
        saidaEm: j['saidaEm']?.toString(),
      );
}

class AtividadeResumo {
  final int id;
  final int idFilial;
  final String tipo;
  final String? subtipo;
  final String status;
  final int? idViagemLegado;
  final String? placaVeiculo;
  final String? iniciadaEm;
  final String? finalizadaEm;
  final List<Participante> participantes;

  AtividadeResumo({
    required this.id,
    required this.idFilial,
    required this.tipo,
    this.subtipo,
    required this.status,
    this.idViagemLegado,
    this.placaVeiculo,
    this.iniciadaEm,
    this.finalizadaEm,
    required this.participantes,
  });

  int get participantesAtivos => participantes.where((p) => p.ativo).length;

  factory AtividadeResumo.fromJson(Map<String, dynamic> j) => AtividadeResumo(
        id: (j['id'] as num).toInt(),
        idFilial: (j['idFilial'] as num).toInt(),
        tipo: j['tipo'],
        subtipo: j['subtipo'],
        status: j['status'],
        idViagemLegado: _asInt(j['idViagemLegado']),
        placaVeiculo: j['placaVeiculo'],
        iniciadaEm: j['iniciadaEm']?.toString(),
        finalizadaEm: j['finalizadaEm']?.toString(),
        participantes: ((j['participantes'] as List?) ?? [])
            .map((e) => Participante.fromJson(e as Map<String, dynamic>))
            .toList(),
      );
}

class CteDescarga {
  final int idConhecimento;
  final int? cte;
  final String? notasFiscais;
  final double volumes;
  final double peso;
  final String? remetente;
  final String? destinatario;
  final String? cidadeDestino;

  CteDescarga({
    required this.idConhecimento,
    this.cte,
    this.notasFiscais,
    required this.volumes,
    required this.peso,
    this.remetente,
    this.destinatario,
    this.cidadeDestino,
  });

  factory CteDescarga.fromJson(Map<String, dynamic> j) => CteDescarga(
        idConhecimento: (j['idConhecimento'] as num).toInt(),
        cte: _asInt(j['cte']),
        notasFiscais: j['notasFiscais'],
        volumes: _asDouble(j['volumes']) ?? 0,
        peso: _asDouble(j['peso']) ?? 0,
        remetente: j['remetente'],
        destinatario: j['destinatario'],
        cidadeDestino: j['cidadeDestino'],
      );
}

class DocumentoOperacional {
  final int? id;
  final int? numeroCte;
  final int? idConhecimentoLegado;
  final bool preCte;
  final int? volumes;
  final double? peso;
  final String? remetente;
  final String? destinatario;
  final String? cidadeDestino;
  final String? chaveNf;
  final String status;
  final int? idLocalAtual;

  DocumentoOperacional({
    this.id,
    this.numeroCte,
    this.idConhecimentoLegado,
    required this.preCte,
    this.volumes,
    this.peso,
    this.remetente,
    this.destinatario,
    this.cidadeDestino,
    this.chaveNf,
    required this.status,
    this.idLocalAtual,
  });

  factory DocumentoOperacional.fromJson(Map<String, dynamic> j) => DocumentoOperacional(
        id: _asInt(j['id']),
        numeroCte: _asInt(j['numeroCte']),
        idConhecimentoLegado: _asInt(j['idConhecimentoLegado']),
        preCte: j['preCte'] ?? false,
        volumes: _asInt(j['volumes']),
        peso: _asDouble(j['peso']),
        remetente: j['remetente'],
        destinatario: j['destinatario'],
        cidadeDestino: j['cidadeDestino'],
        chaveNf: j['chaveNf'],
        status: j['status'],
        idLocalAtual: _asInt(j['idLocalAtual']),
      );
}

class LocalArmazem {
  final int id;
  final String codigo;
  final String nome;
  final String tipo;
  final bool ativo;

  LocalArmazem({
    required this.id,
    required this.codigo,
    required this.nome,
    required this.tipo,
    required this.ativo,
  });

  factory LocalArmazem.fromJson(Map<String, dynamic> j) => LocalArmazem(
        id: (j['id'] as num).toInt(),
        codigo: j['codigo'] ?? '',
        nome: j['nome'] ?? '',
        tipo: j['tipo'] ?? '',
        ativo: j['ativo'] ?? true,
      );
}

class AtividadeFinalizada {
  final int id;
  final int duracaoSegundos;
  final int horasHomemSegundos;
  final int totalParticipantes;

  AtividadeFinalizada({
    required this.id,
    required this.duracaoSegundos,
    required this.horasHomemSegundos,
    required this.totalParticipantes,
  });

  factory AtividadeFinalizada.fromJson(Map<String, dynamic> j) => AtividadeFinalizada(
        id: (j['id'] as num).toInt(),
        duracaoSegundos: _asInt(j['duracaoSegundos']) ?? 0,
        horasHomemSegundos: _asInt(j['horasHomemSegundos']) ?? 0,
        totalParticipantes: _asInt(j['totalParticipantes']) ?? 0,
      );
}
