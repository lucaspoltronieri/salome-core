package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyCte;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LegacyQuoteChain;
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

    /** CT-es autorizados e não cancelados, por id de conhecimento (amarração vinda da coleta). */
    List<LegacyCte> findCtesByIds(Collection<Long> cteIds);

    /**
     * Corrente cotação → coleta → CT-e: uma linha por coleta das cotações informadas, com o CT-e
     * gerado no retorno dela quando já existir. Vazio enquanto o legado não gravar
     * {@code coleta.idCotacao}.
     */
    List<LegacyQuoteChain> findQuoteChains(Collection<Long> quoteIds);

    /** Cotações APROVADAS de qualquer responsável, com data da aprovação a partir de {@code from}. */
    List<LegacyQuote> findApprovedQuotesSince(LocalDate from);

    /** Cotações de qualquer responsável, ainda não aprovadas, criadas a partir de {@code from}. */
    List<LegacyQuote> findApprovableQuotes(LocalDate from);

    /** CNPJ (só dígitos) do único cliente cadastrado com essa razão social; vazio se não houver ou se houver mais de um. */
    Optional<String> findClientCnpjByLegalName(String legalName);
}
