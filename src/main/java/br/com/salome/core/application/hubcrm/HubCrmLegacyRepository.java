package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuotePrint;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HubCrmLegacyRepository {
    List<LegacyCrmClient> findEligibleClients(long cutoffClientId);
    List<LegacyQuote> findQuotesAfter(long quoteId);
    List<LegacyQuote> findQuotesByIds(Collection<Long> quoteIds);
    Optional<LegacyQuotePrint> findQuotePrint(long quoteId);
}
