package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ArpaSuiteGateway {
    void validateCatalog();
    Optional<ArpaDeal> findDeal(long dealId);
    Optional<ArpaDeal> findLatestOpenDealByCnpj(String cnpj);
    long createOrganization(String legalName);
    long createPerson(String name, String phone, long organizationId);
    void updateOrganization(long organizationId, String legalName);
    void updatePerson(long peopleId, String name, String phone, long organizationId);
    void linkDeal(long dealId, long organizationId, long peopleId, long userId);
    long createPortfolioDeal(LegacyCrmClient client, long organizationId, long peopleId, long userId);
    long createPortfolioDealWithoutPerson(LegacyCrmClient client, long organizationId, long userId);
    void updatePortfolioDeal(long dealId, LegacyCrmClient client, long organizationId,
            long peopleId, long userId);
    void updatePortfolioDealWithoutPerson(long dealId, LegacyCrmClient client,
            long organizationId, long userId);
    long createQuoteDeal(LegacyQuote quote, long organizationId, long peopleId, long userId);
    void updateDealFromQuote(long dealId, LegacyQuote quote, long userId);
    long addAnnotation(long dealId, String text);
    void markWon(long dealId, LocalDateTime wonAt);
    void markLost(long dealId, LossReason reason, LocalDateTime lostAt);
    boolean hasWhatsappChannel();
    void sendQuoteDocument(long peopleId, long dealId, String mediaUrl, String caption);

    record ArpaDeal(long id, Long organizationId, Long peopleId, Long userId) {}
}
