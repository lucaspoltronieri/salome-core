package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuotePrint;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HubCrmLegacyRepository {
    List<LegacyCrmClient> findEligibleClients(long cutoffClientId);
    List<LegacyQuote> findQuotesAfter(long quoteId);
    List<LegacyQuote> findQuotesByIds(Collection<Long> quoteIds);
    Optional<LegacyQuotePrint> findQuotePrint(long quoteId);

    /** CT-es autorizados e não cancelados emitidos a partir de {@code since}. */
    List<LegacyCte> findRecentCtes(LocalDate since);

    /** Cotações de qualquer responsável, ainda não aprovadas, criadas a partir de {@code from}. */
    List<LegacyQuote> findApprovableQuotes(LocalDate from);
}
