-- Acompanhamento da cotação depois de aprovada, seja pelo Hub (CT-e) ou à mão pelo comercial.
-- A linha nasce quando a cotação é aprovada, sem CT-e nenhum, e vai sendo completada: a coleta
-- lançada pelo legado na aprovação (coleta.idCotacao), o CT-e do retorno da coleta
-- (conhecimento.idColeta) e, quando o prazo estoura sem CT-e, a triagem.
--
-- Tabela própria porque hub_crm_cte_match é indexada pelo CT-e (id_conhecimento NOT NULL, único) e
-- aqui a maioria das linhas ainda não tem CT-e. A amarração em si continua sendo gravada lá.
CREATE TABLE hub_crm_quote_approval (
  legacy_quote_id      BIGINT NOT NULL,
  quote_responsavel    VARCHAR(80) NULL,
  aprovada_em          DATETIME NULL,
  origem_aprovacao     VARCHAR(10) NOT NULL,   -- HUB | MANUAL
  id_coleta            BIGINT NULL,
  coleta_status        VARCHAR(20) NULL,       -- PENDENTE | EM VIAGEM | REALIZADA | CANCELADA
  id_conhecimento      BIGINT NULL,
  cte_numero           VARCHAR(20) NULL,
  cte_serie            VARCHAR(10) NULL,
  cte_emissao          DATE NULL,
  amarracao            VARCHAR(20) NULL,       -- COLETA | CTE
  status               VARCHAR(30) NOT NULL,   -- SEM_CTE | AMARRADA | REPROVADA_SEM_CTE | REVISAO
  dias_desde_aprovacao INT NULL,
  detalhe              VARCHAR(1000) NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (legacy_quote_id),
  KEY ix_hub_quote_approval_status (status),
  KEY ix_hub_quote_approval_aprovada (aprovada_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
