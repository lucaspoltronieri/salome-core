package br.com.salome.core.infrastructure.legacy.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class LegacyHubCrmRepositoryTest {
    private JdbcTemplate jdbc;
    private LegacyHubCrmRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        repository = new LegacyHubCrmRepository(jdbc);
    }

    /**
     * O SQL da corrente tem '%CANCEL%' e '0000-00-00': montar os placeholders com
     * {@code String.formatted} estourava UnknownFormatConversionException em produção (v1.22.0).
     */
    @Test
    void consultaDaCorrenteMontaOsPlaceholdersSemQuebrarNoPorCento() {
        repository.findQuoteChains(List.of(15900L, 15901L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("co.idCotacao IN (?,?)")
                .contains("%CANCEL%")
                .doesNotContain(":ids");
    }

    @Test
    void listaVaziaNaoConsultaOLegado() {
        assertThat(repository.findQuoteChains(List.of())).isEmpty();
        assertThat(repository.findCtesByIds(List.of())).isEmpty();
        verify(jdbc, org.mockito.Mockito.never()).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    @Test
    void ctesPorIdUsamOsMesmosFiltrosDeCancelamento() {
        repository.findCtesByIds(List.of(710816L));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(sql.getValue())
                .contains("c.idConhecimento IN (?)")
                .contains("%INUTILIZ%")
                .contains("cteCancelado");
    }
}
