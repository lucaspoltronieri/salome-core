package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.InactiveClientReport;
import java.util.Optional;

public interface InactiveClientRepository {
    Optional<InactiveClientReport> findReport(long clientId, int year);

    /**
     * CT-es em que o cliente foi o destinatário sem pagar o frete (cliente "não pagante", do
     * estágio Não Pagantes), do mais antigo para o mais recente.
     */
    Optional<InactiveClientReport> findReceivedReport(long clientId);
}
