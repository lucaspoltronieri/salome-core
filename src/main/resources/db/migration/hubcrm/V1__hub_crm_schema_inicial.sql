CREATE TABLE hub_crm_client (
  id                         BIGINT NOT NULL AUTO_INCREMENT,
  legacy_client_id           BIGINT NOT NULL,
  cnpj                       VARCHAR(14) NOT NULL,
  razao_social               VARCHAR(180) NOT NULL,
  razao_social_normalizada   VARCHAR(180) NOT NULL,
  organization_id            BIGINT NULL,
  people_id                  BIGINT NULL,
  deal_id                    BIGINT NULL,
  assigned_user_id           BIGINT NULL,
  first_cte_without_freight  DATE NOT NULL,
  snapshot_hash              CHAR(64) NOT NULL,
  sync_status                VARCHAR(40) NOT NULL,
  attempt_count              INT NOT NULL DEFAULT 0,
  last_error                 VARCHAR(2000) NULL,
  last_synced_at             DATETIME NULL,
  created_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                 DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hub_client_legacy (legacy_client_id),
  UNIQUE KEY uk_hub_client_cnpj (cnpj),
  KEY ix_hub_client_razao (razao_social_normalizada),
  KEY ix_hub_client_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hub_crm_quote (
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  legacy_quote_id       BIGINT NOT NULL,
  payer_cnpj            VARCHAR(14) NOT NULL,
  legacy_responsible    VARCHAR(80) NOT NULL,
  legacy_status         VARCHAR(30) NOT NULL,
  deal_id               BIGINT NULL,
  organization_id       BIGINT NULL,
  people_id             BIGINT NULL,
  assigned_user_id      BIGINT NULL,
  total_freight         DECIMAL(15,2) NOT NULL,
  snapshot_hash         CHAR(64) NOT NULL,
  annotation_id         BIGINT NULL,
  pdf_status            VARCHAR(40) NOT NULL DEFAULT 'PENDENTE',
  whatsapp_status       VARCHAR(40) NOT NULL DEFAULT 'AGUARDANDO_CANAL',
  sync_status           VARCHAR(40) NOT NULL,
  attempt_count         INT NOT NULL DEFAULT 0,
  last_error            VARCHAR(2000) NULL,
  last_synced_at        DATETIME NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hub_quote_legacy (legacy_quote_id),
  KEY ix_hub_quote_deal (deal_id),
  KEY ix_hub_quote_status (sync_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hub_crm_event (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  event_key          VARCHAR(180) NOT NULL,
  entity_type        VARCHAR(30) NOT NULL,
  entity_id          BIGINT NOT NULL,
  event_type         VARCHAR(50) NOT NULL,
  payload_json       JSON NULL,
  response_summary   VARCHAR(2000) NULL,
  status             VARCHAR(40) NOT NULL,
  attempt_count      INT NOT NULL DEFAULT 0,
  next_attempt_at    DATETIME NULL,
  last_error         VARCHAR(2000) NULL,
  created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  processed_at       DATETIME NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_hub_event_key (event_key),
  KEY ix_hub_event_pending (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE hub_crm_checkpoint (
  checkpoint_key     VARCHAR(80) NOT NULL,
  numeric_value      BIGINT NULL,
  text_value         VARCHAR(500) NULL,
  updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (checkpoint_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO hub_crm_checkpoint (checkpoint_key, numeric_value)
VALUES ('round_robin_cursor', 0), ('last_client_id', 32000), ('last_quote_id', 0);
