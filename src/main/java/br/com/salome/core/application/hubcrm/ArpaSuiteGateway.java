package br.com.salome.core.application.hubcrm;

import br.com.salome.core.domain.hubcrm.LegacyCrmClient;
import br.com.salome.core.domain.hubcrm.LegacyQuote;
import br.com.salome.core.domain.hubcrm.LossReason;
import java.time.LocalDateTime;
import java.util.Optional;

public interface ArpaSuiteGateway {
    void validateCatalog();
    Optional<ArpaDeal> findDeal(long dealId);
    /** Estágio atual do card; vazio quando o card foi apagado (404). */
    Optional<Long> findDealStage(long dealId);
    Optional<ArpaDeal> findDealByLegacyQuoteId(long legacyQuoteId);
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
    /** Perde a negociação com um motivo do ArpaSuite que não existe no legado (ex.: 317833, sem tratativa). */
    void markLostWithReasonId(long dealId, long lostReasonId);
    /** Status da negociação (open, won, lost); vazio se o card foi apagado. */
    Optional<String> findDealStatus(long dealId);
    /** Alguma atividade (ligação, WhatsApp, reunião, tarefa, nota) não cancelada no card, criada a partir de {@code since}. */
    boolean hasDealActivitySince(long dealId, java.time.LocalDate since);
    boolean hasWhatsappChannel();
    /**
     * Conversa do canal com janela de 24h aberta (o contato mandou mensagem nas últimas 24h) ligada
     * à cotação: mesma pessoa, mesmo telefone, pessoa da mesma organização ou com o mesmo nome.
     */
    Optional<OpenConversation> findOpenConversation(long peopleId, String phone, Long organizationId,
            String... names);

    /** Envia o PDF dentro da conversa (sem template). */
    void sendDocumentToConversation(long conversationId, long dealId, String mediaUrl, String caption);

    record OpenConversation(long id, String match) {}

    /** `cnpj` = campo CNPJ do card (só dígitos), para saber se o pagador da cotação mudou. */
    record ArpaDeal(long id, Long organizationId, Long peopleId, Long userId, String cnpj) {
        public ArpaDeal(long id, Long organizationId, Long peopleId, Long userId) {
            this(id, organizationId, peopleId, userId, null);
        }
    }
}
