package br.com.salome.core.domain.torre;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Mapa completo do armazém de uma filial (Torre operacional): o ciclo de vida da
 * carga numa visão só — chegando, no armazém (aguardando/descarregando/armazenado)
 * e saindo para entrega. Composto ao vivo do legado + estados próprios da Torre.
 */
public record MapaArmazemSnapshot(
        int idFilial,
        Instant atualizadoEm,
        LocalDate dataCorte,
        List<MapaCaminhao> vindoDeOutrasBases,
        List<ViagemAguardando> aguardandoDescarga,
        List<AtividadeResumo> descarregando,
        List<MapaCte> armazenado,
        List<MapaCaminhao> emRotaEntrega,
        List<MapaCte> outrosArmazens
) {
}
