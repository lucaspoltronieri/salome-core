package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.ViagemAguardando;
import java.time.LocalDate;
import java.util.List;

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
}
