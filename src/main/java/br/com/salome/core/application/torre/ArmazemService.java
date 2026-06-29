package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.ArmazemSnapshot;
import br.com.salome.core.domain.torre.BoxOcupacao;
import br.com.salome.core.domain.torre.DocumentoArmazenado;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * "Armazém atual" da Torre: o que está fisicamente no galpão pelos estados
 * próprios da Torre (não o status grosso do legado), agrupado por box. Fonte da
 * verdade são os estados que o app de armazém controla (descarga → separação →
 * carregamento); enquanto o app não estiver 100% em uso, a visão fica esparsa —
 * o que é esperado.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class ArmazemService {

    private final DocumentoRepository documentoRepository;
    private final ConhecimentoLegadoRepository conhecimentoRepository;
    private final Clock clock;

    public ArmazemService(DocumentoRepository documentoRepository,
                          ConhecimentoLegadoRepository conhecimentoRepository,
                          Clock clock) {
        this.documentoRepository = documentoRepository;
        this.conhecimentoRepository = conhecimentoRepository;
        this.clock = clock;
    }

    public ArmazemSnapshot snapshot(int idFilial) {
        List<DocumentoArmazenado> documentos = enriquecerEmissao(documentoRepository.listarArmazenados(idFilial));
        return new ArmazemSnapshot(idFilial, clock.instant(), agruparPorBox(documentos), documentos);
    }

    /**
     * Preenche a data de emissão (não guardada pela Torre) buscando no legado em
     * lote por idConhecimento. Pré-CTes sem CT-e casado ficam sem data.
     */
    private List<DocumentoArmazenado> enriquecerEmissao(List<DocumentoArmazenado> documentos) {
        Set<Long> ids = documentos.stream()
                .map(DocumentoArmazenado::idConhecimentoLegado)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return documentos;
        }
        Map<Long, LocalDate> emissoes = conhecimentoRepository.emissaoPorConhecimento(ids);
        return documentos.stream()
                .map(d -> d.idConhecimentoLegado() == null
                        ? d
                        : d.comDataEmissao(emissoes.get(d.idConhecimentoLegado())))
                .toList();
    }

    /** Agrupa os documentos por box/local, somando volumes/peso e contando por estágio. */
    List<BoxOcupacao> agruparPorBox(List<DocumentoArmazenado> documentos) {
        Map<Long, Acumulador> grupos = new LinkedHashMap<>();
        for (DocumentoArmazenado d : documentos) {
            Acumulador a = grupos.computeIfAbsent(d.idLocal(), k -> new Acumulador(d));
            a.somar(d);
        }
        List<BoxOcupacao> boxes = new ArrayList<>(grupos.size());
        for (Acumulador a : grupos.values()) {
            boxes.add(a.toBox());
        }
        return boxes;
    }

    /** Acumulador mutável por box (id_local pode ser null = "sem box"). */
    private static final class Acumulador {
        private final Long idLocal;
        private final String codigo;
        private final String nome;
        private final String tipo;
        private int total;
        private int aguardandoSeparacao;
        private int emSeparacao;
        private int prontos;
        private int emCarregamento;
        private int avarias;
        private int volumes;
        private BigDecimal peso = BigDecimal.ZERO;

        Acumulador(DocumentoArmazenado primeiro) {
            this.idLocal = primeiro.idLocal();
            this.codigo = primeiro.codigoLocal();
            this.nome = primeiro.idLocal() == null ? "Sem box" : primeiro.nomeLocal();
            this.tipo = primeiro.tipoLocal();
        }

        void somar(DocumentoArmazenado d) {
            total++;
            volumes += d.volumes() == null ? 0 : d.volumes();
            if (d.peso() != null) {
                peso = peso.add(d.peso());
            }
            switch (d.status()) {
                case NO_ARMAZEM -> aguardandoSeparacao++;
                case EM_SEPARACAO -> emSeparacao++;
                case SEPARADO_BOX -> prontos++;
                case EM_CARREGAMENTO -> emCarregamento++;
                case AVARIA, DIVERGENCIA -> avarias++;
                default -> { /* estados fora do galpão não chegam aqui */ }
            }
        }

        BoxOcupacao toBox() {
            return new BoxOcupacao(idLocal, codigo, nome, tipo, total,
                    aguardandoSeparacao, emSeparacao, prontos, emCarregamento, avarias, volumes, peso);
        }
    }

    /** Conta um conjunto de documentos por estágio (reuso pelo dashboard). */
    static int[] contarPorEstagio(List<DocumentoArmazenado> documentos) {
        int aguardando = 0, separacao = 0, prontos = 0, carregamento = 0, avarias = 0;
        for (DocumentoArmazenado d : documentos) {
            switch (d.status()) {
                case NO_ARMAZEM -> aguardando++;
                case EM_SEPARACAO -> separacao++;
                case SEPARADO_BOX -> prontos++;
                case EM_CARREGAMENTO -> carregamento++;
                case AVARIA, DIVERGENCIA -> avarias++;
                default -> { }
            }
        }
        return new int[] {aguardando, separacao, prontos, carregamento, avarias};
    }
}
