package br.com.salome.core.infrastructure.legacy.financeiro;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyCteSemFaturaRepositoryTest {

    private JdbcTemplate jdbcTemplate;
    private LegacyCteSemFaturaRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ctes-sem-fatura;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new LegacyCteSemFaturaRepository(jdbcTemplate);

        jdbcTemplate.execute("DROP TABLE IF EXISTS comprovanteentrega");
        jdbcTemplate.execute("DROP TABLE IF EXISTS conhecimento");
        jdbcTemplate.execute("DROP TABLE IF EXISTS cliente");
        jdbcTemplate.execute("""
                CREATE TABLE cliente (
                    idCliente INT PRIMARY KEY,
                    razaoSocial VARCHAR(255)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE conhecimento (
                    idConhecimento INT PRIMARY KEY,
                    cte INT,
                    cteEmissao DATE,
                    idClienteEmitente INT,
                    idClienteDestinatario INT,
                    situacao VARCHAR(50),
                    valorTotal DECIMAL(15, 2),
                    idFatura INT,
                    cteCancelado DATE
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE comprovanteentrega (
                    idComprovanteEntrega INT PRIMARY KEY,
                    idConhecimento INT,
                    dataEntrega DATE
                )
                """);
    }

    @Test
    void shouldFilterSemFaturaAndUseLatestDeliveryProofDate() {
        insertCliente(1, "Remetente A");
        insertCliente(2, "Destinatario B");
        insertConhecimento(10, 1001, "2026-05-31", 1, 2, "Finalizada", null, null);
        insertConhecimento(11, 1002, "2026-05-31", 1, 2, "Finalizada", 99, null);
        insertConhecimento(12, 1003, "2026-05-31", 1, 2, "Cancelada", null, null);
        insertConhecimento(13, 1004, "2026-05-31", 1, 2, "Inutilizada", null, null);
        insertConhecimento(14, 1005, "2026-05-31", 1, 2, "Finalizada", null, "2026-06-01");
        insertConhecimento(15, 1006, "2026-06-01", 1, 2, "Finalizada", null, null);
        insertConhecimento(16, 1007, "2026-05-31", 1, 2, "Denegada", null, null);
        insertComprovante(1, 10, "2026-06-01");
        insertComprovante(2, 10, "2026-06-03");

        var rows = repository.listarEmitidosSemFaturaAte(LocalDate.of(2026, 5, 31));

        assertEquals(1, rows.size());
        var row = rows.getFirst();
        assertEquals(1001, row.numeroCte());
        assertEquals(LocalDate.of(2026, 5, 31), row.dataEmissao());
        assertEquals("Remetente A", row.remetente());
        assertEquals("Destinatario B", row.destinatario());
        assertEquals("Finalizada", row.statusCte());
        assertEquals(new BigDecimal("1234.56"), row.totalFrete());
        assertEquals(LocalDate.of(2026, 6, 3), row.dataEntrega());
    }

    private void insertCliente(int idCliente, String razaoSocial) {
        jdbcTemplate.update("INSERT INTO cliente (idCliente, razaoSocial) VALUES (?, ?)", idCliente, razaoSocial);
    }

    private void insertConhecimento(int idConhecimento, int cte, String cteEmissao, int idClienteEmitente,
            int idClienteDestinatario, String situacao, Integer idFatura, String cteCancelado) {
        jdbcTemplate.update("""
                INSERT INTO conhecimento (
                    idConhecimento, cte, cteEmissao, idClienteEmitente, idClienteDestinatario,
                    situacao, valorTotal, idFatura, cteCancelado
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, idConhecimento, cte, cteEmissao, idClienteEmitente, idClienteDestinatario, situacao,
                new BigDecimal("1234.56"), idFatura, cteCancelado);
    }

    private void insertComprovante(int idComprovanteEntrega, int idConhecimento, String dataEntrega) {
        jdbcTemplate.update("""
                INSERT INTO comprovanteentrega (idComprovanteEntrega, idConhecimento, dataEntrega)
                VALUES (?, ?, ?)
                """, idComprovanteEntrega, idConhecimento, dataEntrega);
    }
}
