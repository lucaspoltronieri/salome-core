-- =====================================================================
-- Torre de Controle Operacional do Armazém - schema inicial
-- Banco próprio (MySQL read-write), separado do legado.
-- Unidade base = CT-e. Rastreio por volume/etiqueta é evolução futura.
-- =====================================================================

-- Filiais participantes da Torre. Rollout por filial (Rio Preto primeiro):
-- ativar = ativa=TRUE + definir data_corte_viagem (só considera viagens
-- com baixa a partir dessa data, pois o legado não registra a descarga).
CREATE TABLE filial_torre (
  id_filial          INT          NOT NULL,
  nome               VARCHAR(120) NOT NULL,
  data_corte_viagem  DATE         NOT NULL,
  ativa              BOOLEAN      NOT NULL DEFAULT FALSE,
  criado_em          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_filial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Perfis de acesso (escopo de filial).
CREATE TABLE perfil (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  codigo    VARCHAR(40)  NOT NULL,
  descricao VARCHAR(120) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_perfil_codigo (codigo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Usuários da Torre (cadastro próprio, login individual).
CREATE TABLE usuario (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  login      VARCHAR(60)  NOT NULL,
  nome       VARCHAR(120) NOT NULL,
  senha_hash VARCHAR(100) NOT NULL,
  id_filial  INT          NOT NULL,
  id_perfil  BIGINT       NOT NULL,
  ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
  criado_em  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_usuario_login (login),
  KEY ix_usuario_filial (id_filial),
  CONSTRAINT fk_usuario_perfil FOREIGN KEY (id_perfil) REFERENCES perfil(id),
  CONSTRAINT fk_usuario_filial FOREIGN KEY (id_filial) REFERENCES filial_torre(id_filial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Locais físicos do armazém (docas, boxes, áreas, pendência, avaria...).
CREATE TABLE local_armazem (
  id        BIGINT       NOT NULL AUTO_INCREMENT,
  id_filial INT          NOT NULL,
  codigo    VARCHAR(40)  NOT NULL,
  nome      VARCHAR(120) NOT NULL,
  tipo      VARCHAR(40)  NOT NULL, -- DOCA|BOX|AREA|PENDENCIA|AVARIA|QUIMICA|BOX_DEFINITIVO
  ativo     BOOLEAN      NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_local_filial_codigo (id_filial, codigo),
  KEY ix_local_filial (id_filial),
  CONSTRAINT fk_local_filial FOREIGN KEY (id_filial) REFERENCES filial_torre(id_filial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cabeçalho da atividade operacional.
CREATE TABLE atividade_armazem (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  id_filial        INT          NOT NULL,
  tipo             VARCHAR(40)  NOT NULL, -- DESCARGA_TRANSFERENCIA|DESCARGA_COLETA|SEPARACAO|CARREGAMENTO|OUTRAS
  subtipo          VARCHAR(60)  NULL,
  status           VARCHAR(40)  NOT NULL, -- ABERTA|FINALIZADA|CANCELADA
  id_viagem_legado BIGINT       NULL,
  placa_veiculo    VARCHAR(20)  NULL,
  id_responsavel   BIGINT       NULL,
  observacao       VARCHAR(500) NULL,
  iniciada_em      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finalizada_em    DATETIME     NULL,
  PRIMARY KEY (id),
  KEY ix_atividade_filial_status (id_filial, status),
  KEY ix_atividade_viagem (id_viagem_legado),
  CONSTRAINT fk_atividade_filial FOREIGN KEY (id_filial) REFERENCES filial_torre(id_filial),
  CONSTRAINT fk_atividade_responsavel FOREIGN KEY (id_responsavel) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Tempo individual de cada pessoa na atividade.
-- Regra "1 participação ativa por usuário" (saida_em IS NULL) é garantida na
-- aplicação (MySQL não suporta índice único filtrado).
CREATE TABLE atividade_participante (
  id           BIGINT      NOT NULL AUTO_INCREMENT,
  id_atividade BIGINT      NOT NULL,
  id_usuario   BIGINT      NOT NULL,
  funcao       VARCHAR(40) NULL,
  entrada_em   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  saida_em     DATETIME    NULL,
  dispositivo  VARCHAR(80) NULL,
  origem       VARCHAR(40) NULL, -- APP|PAINEL|AJUSTE
  PRIMARY KEY (id),
  KEY ix_part_atividade (id_atividade),
  KEY ix_part_usuario_ativo (id_usuario, saida_em),
  CONSTRAINT fk_part_atividade FOREIGN KEY (id_atividade) REFERENCES atividade_armazem(id),
  CONSTRAINT fk_part_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cópia operacional do CT-e (ou pré-CTe de coleta).
CREATE TABLE documento_operacional (
  id                     BIGINT        NOT NULL AUTO_INCREMENT,
  id_filial              INT           NOT NULL,
  numero_cte             BIGINT        NULL,
  id_conhecimento_legado BIGINT        NULL,
  id_viagem_legado       BIGINT        NULL,
  pre_cte                BOOLEAN       NOT NULL DEFAULT FALSE,
  chave_nf               VARCHAR(60)   NULL, -- chave principal p/ casamento (coleta)
  volumes                INT           NULL,
  peso                   DECIMAL(12,3) NULL,
  remetente              VARCHAR(160)  NULL,
  destinatario           VARCHAR(160)  NULL,
  cidade_destino         VARCHAR(120)  NULL,
  status                 VARCHAR(40)   NOT NULL, -- ver catálogo de status
  id_local_atual         BIGINT        NULL,
  atualizado_em          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  criado_em              DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_doc_filial_status (id_filial, status),
  KEY ix_doc_cte (numero_cte),
  KEY ix_doc_chave_nf (chave_nf),
  KEY ix_doc_viagem (id_viagem_legado),
  CONSTRAINT fk_doc_filial FOREIGN KEY (id_filial) REFERENCES filial_torre(id_filial),
  CONSTRAINT fk_doc_local FOREIGN KEY (id_local_atual) REFERENCES local_armazem(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- NFs de um documento (um CT-e agrupa várias; coleta = pré-CTe por NF).
CREATE TABLE documento_nf (
  id            BIGINT      NOT NULL AUTO_INCREMENT,
  id_documento  BIGINT      NOT NULL,
  chave_nf      VARCHAR(60) NULL,
  numero_nf     VARCHAR(20) NULL,
  serie_nf      VARCHAR(10) NULL,
  cnpj_emitente VARCHAR(20) NULL,
  PRIMARY KEY (id),
  KEY ix_docnf_documento (id_documento),
  KEY ix_docnf_chave (chave_nf),
  CONSTRAINT fk_docnf_documento FOREIGN KEY (id_documento) REFERENCES documento_operacional(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Vínculo atividade <-> documento (papel no processo).
CREATE TABLE atividade_documento (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  id_atividade  BIGINT        NOT NULL,
  id_documento  BIGINT        NOT NULL,
  papel         VARCHAR(40)   NOT NULL, -- DESCARREGADO|SEPARADO|CARREGADO
  volumes       INT           NULL,
  peso          DECIMAL(12,3) NULL,
  id_usuario    BIGINT        NULL,
  registrado_em DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_atvdoc_atividade (id_atividade),
  KEY ix_atvdoc_documento (id_documento),
  CONSTRAINT fk_atvdoc_atividade FOREIGN KEY (id_atividade) REFERENCES atividade_armazem(id),
  CONSTRAINT fk_atvdoc_documento FOREIGN KEY (id_documento) REFERENCES documento_operacional(id),
  CONSTRAINT fk_atvdoc_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Evento de movimentação do documento (inclui cross-dock origem->destino).
CREATE TABLE movimento_documento (
  id                   BIGINT      NOT NULL AUTO_INCREMENT,
  id_documento         BIGINT      NOT NULL,
  tipo                 VARCHAR(40) NOT NULL, -- DESCARGA|ARMAZENAGEM|SEPARACAO|CARREGAMENTO|CROSS_DOCK
  id_local_origem      BIGINT      NULL,
  id_local_destino     BIGINT      NULL,
  id_atividade_origem  BIGINT      NULL,
  id_atividade_destino BIGINT      NULL,
  id_usuario           BIGINT      NULL,
  ocorrido_em          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_mov_documento (id_documento),
  CONSTRAINT fk_mov_documento FOREIGN KEY (id_documento) REFERENCES documento_operacional(id),
  CONSTRAINT fk_mov_local_origem FOREIGN KEY (id_local_origem) REFERENCES local_armazem(id),
  CONSTRAINT fk_mov_local_destino FOREIGN KEY (id_local_destino) REFERENCES local_armazem(id),
  CONSTRAINT fk_mov_atv_origem FOREIGN KEY (id_atividade_origem) REFERENCES atividade_armazem(id),
  CONSTRAINT fk_mov_atv_destino FOREIGN KEY (id_atividade_destino) REFERENCES atividade_armazem(id),
  CONSTRAINT fk_mov_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Ocorrências operacionais (avaria, divergência, falta, excesso, pendência).
CREATE TABLE ocorrencia_operacional (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  id_filial     INT          NOT NULL,
  tipo          VARCHAR(40)  NOT NULL,
  id_documento  BIGINT       NULL,
  id_atividade  BIGINT       NULL,
  placa_veiculo VARCHAR(20)  NULL,
  descricao     VARCHAR(500) NULL,
  foto_path     VARCHAR(255) NULL,
  id_usuario    BIGINT       NULL,
  criado_em     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_ocorr_filial (id_filial),
  CONSTRAINT fk_ocorr_filial FOREIGN KEY (id_filial) REFERENCES filial_torre(id_filial),
  CONSTRAINT fk_ocorr_documento FOREIGN KEY (id_documento) REFERENCES documento_operacional(id),
  CONSTRAINT fk_ocorr_atividade FOREIGN KEY (id_atividade) REFERENCES atividade_armazem(id),
  CONSTRAINT fk_ocorr_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Log de execução do sincronizador do legado.
CREATE TABLE sincronizacao_legado_log (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  tipo          VARCHAR(40)   NOT NULL, -- VIAGENS_AGUARDANDO|CASAMENTO_PRE_CTE
  id_filial     INT           NULL,
  iniciado_em   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finalizado_em DATETIME      NULL,
  processados   INT           NULL,
  sucesso       BOOLEAN       NULL,
  erro          VARCHAR(1000) NULL,
  PRIMARY KEY (id),
  KEY ix_sync_tipo (tipo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Rastro de auditoria de ações sensíveis.
CREATE TABLE evento_auditoria (
  id          BIGINT        NOT NULL AUTO_INCREMENT,
  id_filial   INT           NULL,
  id_usuario  BIGINT        NULL,
  acao        VARCHAR(60)   NOT NULL,
  entidade    VARCHAR(60)   NULL,
  id_entidade BIGINT        NULL,
  detalhe     VARCHAR(1000) NULL,
  ocorrido_em DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_aud_entidade (entidade, id_entidade)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed dos perfis.
INSERT INTO perfil (codigo, descricao) VALUES
  ('OPERADOR', 'Operador de armazém - acesso restrito à própria filial'),
  ('ADMIN', 'Administrador - acesso a todas as filiais');
