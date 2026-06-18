package br.com.salome.core.infrastructure.pedagio;

import br.com.salome.core.application.financeiro.PedagioRepository;
import br.com.salome.core.domain.financeiro.PedagioLancamento;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Le os extratos de pedagio em {@code classpath:dados/pedagio/*.json} (formato da operadora Move
 * Mais/AUTOBAN: {@code transactions.transaction[]} com {@code plate}, {@code occurrenceDate},
 * {@code historicId} e {@code value} em centavos). Considera como pedagio as Passagens
 * (historicId 1101, sinal positivo) menos os Estornos de Passagem (historicId 2101, negativo);
 * mensalidade da tag e pagamento de fatura ficam de fora. O resultado e cacheado na primeira leitura.
 */
@Repository
public class JsonPedagioRepository implements PedagioRepository {

    private static final Logger log = LoggerFactory.getLogger(JsonPedagioRepository.class);

    private static final String PASSAGEM = "1101";
    private static final String ESTORNO_PASSAGEM = "2101";
    private static final String LOCAL = "classpath*:dados/pedagio/*.json";

    private final ObjectMapper objectMapper;
    private volatile List<PedagioLancamento> cache;

    public JsonPedagioRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PedagioLancamento> listar() {
        List<PedagioLancamento> atual = cache;
        if (atual == null) {
            synchronized (this) {
                if (cache == null) {
                    cache = carregar();
                }
                atual = cache;
            }
        }
        return atual;
    }

    private List<PedagioLancamento> carregar() {
        List<PedagioLancamento> lancamentos = new ArrayList<>();
        try {
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] recursos = resolver.getResources(LOCAL);
            for (Resource recurso : recursos) {
                try (InputStream in = recurso.getInputStream()) {
                    JsonNode raiz = objectMapper.readTree(in);
                    JsonNode lista = raiz.path("transactions").path("transaction");
                    for (JsonNode t : lista) {
                        PedagioLancamento lancamento = converter(t);
                        if (lancamento != null) {
                            lancamentos.add(lancamento);
                        }
                    }
                }
            }
            log.info("Pedagio: {} lancamento(s) carregado(s) de {} arquivo(s).", lancamentos.size(), recursos.length);
        } catch (Exception e) {
            log.warn("Pedagio: falha ao carregar extratos de {} ({}). Seguindo sem pedagio.", LOCAL, e.toString());
        }
        return List.copyOf(lancamentos);
    }

    private PedagioLancamento converter(JsonNode t) {
        String historicId = t.path("historicId").asText("").trim();
        int sinal;
        if (PASSAGEM.equals(historicId)) {
            sinal = 1;
        } else if (ESTORNO_PASSAGEM.equals(historicId)) {
            sinal = -1;
        } else {
            return null;
        }
        String placa = normalizarPlaca(t.path("plate").asText(""));
        if (placa.isBlank()) {
            return null;
        }
        LocalDate data = parseData(t.path("occurrenceDate").asText(""));
        if (data == null) {
            return null;
        }
        long centavos = t.path("value").asLong(0);
        BigDecimal valor = BigDecimal.valueOf(centavos).movePointLeft(2).multiply(BigDecimal.valueOf(sinal));
        return new PedagioLancamento(placa, data, valor);
    }

    static String normalizarPlaca(String placa) {
        if (placa == null) {
            return "";
        }
        return placa.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
    }

    private LocalDate parseData(String valor) {
        if (valor == null || valor.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(valor.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }
}
