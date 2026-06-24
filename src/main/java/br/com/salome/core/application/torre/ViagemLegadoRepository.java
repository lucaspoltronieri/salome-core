package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.ViagemAguardando;
import java.time.LocalDate;
import java.util.List;

/**
 * Leitura do legado (somente leitura) das viagens de transferência aguardando descarga.
 */
public interface ViagemLegadoRepository {

    List<ViagemAguardando> listarAguardandoDescarga(int idFilialDestino, LocalDate dataCorte, int limite);
}
