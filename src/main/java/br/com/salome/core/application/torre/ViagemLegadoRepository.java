package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.ManifestoResumo;
import br.com.salome.core.domain.torre.ResumoViagemLegado;
import br.com.salome.core.domain.torre.ViagemAguardando;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leitura do legado (somente leitura) das viagens aguardando descarga: transferências
 * (chegadas de outra filial) e coletas da própria filial (lançadas na viagem).
 */
public interface ViagemLegadoRepository {

    List<ViagemAguardando> listarAguardandoDescarga(int idFilialDestino, LocalDate dataCorte, int limite);

    /**
     * Viagens trazendo coletas da própria filial ainda em trânsito (status 'Em Viagem'),
     * agrupadas por viagem (caminhão). Só coletas que entraram em viagem a partir de
     * {@code dataCorte} (evita arrastar coletas antigas presas em 'Em Viagem' no legado).
     */
    List<ViagemAguardando> listarColetasAguardando(int idFilial, LocalDate dataCorte, int limite);

    /**
     * Data/hora de baixa mais recente e contagem de manifestos (idViagemTransferencia) de uma
     * viagem — usado pra enriquecer telas de Descarga/Separação com "data de chegada" e
     * "manifesto" quando reabertas sem o contexto rico da lista original.
     * Default vazio para fakes de teste; a implementação real (legado) sobrescreve.
     */
    default Optional<ManifestoResumo> buscarManifestoDaViagem(long idViagem) {
        return Optional.empty();
    }

    /**
     * Resumo agregado (1 linha por viagem) pra enriquecer em lote a lista de caminhões a
     * separar. Default vazio para fakes de teste; a implementação real (legado) sobrescreve.
     */
    default Map<Long, ResumoViagemLegado> buscarResumoPorViagens(Collection<Long> idsViagem) {
        return Map.of();
    }
}
