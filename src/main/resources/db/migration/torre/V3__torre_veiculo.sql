-- =====================================================================
-- Torre - veículos (placas) de saída: usados no carregamento de Entrega e
-- Transferência. O operador marca uma placa existente ou cadastra uma nova.
-- (Descarga usa a placa da viagem do legado; isto é só para a saída.)
-- =====================================================================
CREATE TABLE veiculo (
  id        BIGINT      NOT NULL AUTO_INCREMENT,
  id_filial INT         NOT NULL,
  placa     VARCHAR(20) NOT NULL,
  tipo      VARCHAR(20) NOT NULL, -- ENTREGA|TRANSFERENCIA
  ativo     BOOLEAN     NOT NULL DEFAULT TRUE,
  criado_em DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_veiculo_filial_placa (id_filial, placa),
  KEY ix_veiculo_filial_tipo (id_filial, tipo, ativo),
  CONSTRAINT fk_veiculo_filial FOREIGN KEY (id_filial) REFERENCES filial_torre(id_filial)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed das placas da filial 02 (só insere se a filial existir na Torre).
INSERT INTO veiculo (id_filial, placa, tipo)
SELECT 2, p.placa, 'ENTREGA'
FROM (
  SELECT 'EDL-3H47' AS placa UNION ALL SELECT 'CQH-8J75' UNION ALL SELECT 'DTD-0B79'
  UNION ALL SELECT 'CUD-7816' UNION ALL SELECT 'CUD-8698' UNION ALL SELECT 'BXF-8797'
  UNION ALL SELECT 'DAO-4727' UNION ALL SELECT 'CUD-9C11' UNION ALL SELECT 'DAO-4728'
  UNION ALL SELECT 'DAO-4J21' UNION ALL SELECT 'STC-4J96' UNION ALL SELECT 'CUD-9B99'
) p
WHERE EXISTS (SELECT 1 FROM filial_torre WHERE id_filial = 2);

INSERT INTO veiculo (id_filial, placa, tipo)
SELECT 2, p.placa, 'TRANSFERENCIA'
FROM (
  SELECT 'CUD-8253' AS placa UNION ALL SELECT 'CCU-5F57' UNION ALL SELECT 'DFJ-1E38'
  UNION ALL SELECT 'STB-5A20' UNION ALL SELECT 'SVE-2H49' UNION ALL SELECT 'SWT-6E51'
) p
WHERE EXISTS (SELECT 1 FROM filial_torre WHERE id_filial = 2);
