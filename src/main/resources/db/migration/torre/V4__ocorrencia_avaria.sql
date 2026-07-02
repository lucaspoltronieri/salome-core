-- =====================================================================
-- Torre - Processo de Ocorrência de Avaria (registro no app + tratamento web)
-- Estende ocorrencia_operacional com ciclo de status, culpa, responsáveis,
-- valor total e tempo de preenchimento; adiciona CT-es, fotos e itens (N).
-- =====================================================================

-- Cabeçalho: novos campos do fluxo de avaria.
ALTER TABLE ocorrencia_operacional
  ADD COLUMN status                VARCHAR(30)   NOT NULL DEFAULT 'REGISTRADA' AFTER tipo,
     -- REGISTRADA|EM_ANALISE|AGUARDANDO_PAGAMENTO|RESOLVIDA|FINALIZADA|CANCELADA
  ADD COLUMN culpa                 VARCHAR(20)   NULL AFTER descricao, -- CARREGAMENTO|VIAGEM|DESCARREGAMENTO
  ADD COLUMN quem_causou           VARCHAR(160)  NULL,
  ADD COLUMN responsavel_pagamento VARCHAR(160)  NULL,
  ADD COLUMN valor_total           DECIMAL(12,2) NULL,
  ADD COLUMN data_identificacao    DATETIME      NULL,
  ADD COLUMN duracao_segundos      INT           NULL,   -- tempo de preenchimento no app
  ADD COLUMN motorista             VARCHAR(160)  NULL,    -- herdado da atividade/viagem
  ADD COLUMN id_viagem_legado      BIGINT        NULL,    -- herdado da atividade
  ADD COLUMN tratada_por           BIGINT        NULL,
  ADD COLUMN tratada_em            DATETIME      NULL,
  ADD COLUMN resolucao             VARCHAR(1000) NULL,
  ADD KEY ix_ocorr_filial_status (id_filial, status),
  ADD CONSTRAINT fk_ocorr_tratada_por FOREIGN KEY (tratada_por) REFERENCES usuario(id);

-- CT-es amarrados à ocorrência (herdados/selecionados da atividade/viagem).
CREATE TABLE ocorrencia_cte (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  id_ocorrencia BIGINT       NOT NULL,
  numero_cte    BIGINT       NULL,
  id_documento  BIGINT       NULL,   -- documento_operacional resolvido, se existir
  remetente     VARCHAR(160) NULL,
  destinatario  VARCHAR(160) NULL,
  criado_em     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_octe_ocorrencia (id_ocorrencia),
  CONSTRAINT fk_octe_ocorrencia FOREIGN KEY (id_ocorrencia) REFERENCES ocorrencia_operacional(id),
  CONSTRAINT fk_octe_documento  FOREIGN KEY (id_documento)  REFERENCES documento_operacional(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Fotos da ocorrência (várias; categorizadas). Mesmo esquema de path do FotoStorageService.
CREATE TABLE ocorrencia_foto (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  id_ocorrencia BIGINT       NOT NULL,
  categoria     VARCHAR(20)  NOT NULL DEFAULT 'AVARIA', -- AVARIA|NOTA_FISCAL
  foto_path     VARCHAR(255) NOT NULL,
  ordem         INT          NOT NULL DEFAULT 0,
  criado_em     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_ofoto_ocorrencia (id_ocorrencia, categoria),
  CONSTRAINT fk_ofoto_ocorrencia FOREIGN KEY (id_ocorrencia) REFERENCES ocorrencia_operacional(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Itens avariados (campos livres: código/nome/quantidade; sem valor por item).
CREATE TABLE ocorrencia_item (
  id            BIGINT        NOT NULL AUTO_INCREMENT,
  id_ocorrencia BIGINT        NOT NULL,
  codigo        VARCHAR(60)   NULL,
  nome          VARCHAR(255)  NOT NULL,
  quantidade    DECIMAL(12,3) NOT NULL DEFAULT 1,
  criado_em     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_oitem_ocorrencia (id_ocorrencia),
  CONSTRAINT fk_oitem_ocorrencia FOREIGN KEY (id_ocorrencia) REFERENCES ocorrencia_operacional(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
