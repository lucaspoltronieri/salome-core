package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import java.util.Optional;

public interface InactiveClientRepository {
    Optional<InactiveClientReport> findReport(long clientId, int year);
}
