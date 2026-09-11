-- Aprovação automática da cotação pelo CT-e emitido. Um CT-e aprova no máximo uma
-- cotação e uma cotação é aprovada por no máximo um CT-e. CT-e sem cotação
-- correspondente não é gravado (é reavaliado enquanto estiver na janela de leitura).
CREATE TABLE hub_crm_cte_match (
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  id_conhecimento        BIGINT NOT NULL,
  cte_numero             VARCHAR(20) NULL,
  cte_serie              VARCHAR(10) NULL,
  cte_chave              VARCHAR(60) NULL,
  cte_emissao            DATE NULL,
  cte_frete              DECIMAL(15,2) NULL,
  pagador_cnpj           VARCHAR(14) NULL,
  legacy_quote_id        BIGINT NULL,
  quote_responsavel      VARCHAR(80) NULL,
  quote_status_anterior  VARCHAR(30) NULL,
  quote_frete            DECIMAL(15,2) NULL,
  status                 VARCHAR(30) NOT NULL,
  criterios              VARCHAR(1000) NULL,
  divergencias           VARCHAR(1000) NULL,
  created_at             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hub_cte_match_cte (id_conhecimento),
  UNIQUE KEY uk_hub_cte_match_quote (legacy_quote_id),
  KEY ix_hub_cte_match_status (status),
  KEY ix_hub_cte_match_emissao (cte_emissao)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
