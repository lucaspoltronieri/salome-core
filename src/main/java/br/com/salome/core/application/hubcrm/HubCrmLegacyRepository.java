package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import java.util.Collection;
import java.util.List;

public interface HubCrmLegacyRepository {
    List<LegacyCrmClient> findEligibleClients(long cutoffClientId);
    List<LegacyQuote> findQuotesAfter(long quoteId);
    List<LegacyQuote> findQuotesByIds(Collection<Long> quoteIds);
}
