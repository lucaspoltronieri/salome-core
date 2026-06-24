package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.EventoAuditoria;
import java.util.List;

public interface AuditoriaRepository {

    long registrar(EventoAuditoria evento);

    /** Últimos eventos da filial (mais recentes primeiro). */
    List<EventoAuditoria> listarPorFilial(int idFilial, int limite);
}
