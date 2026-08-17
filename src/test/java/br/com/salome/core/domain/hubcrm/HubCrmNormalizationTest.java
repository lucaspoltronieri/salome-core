package br.com.salome.core.domain.hubcrm;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HubCrmNormalizationTest {
    @Test
    void normalizaCnpjRazaoEAbreviaNome() {
        assertThat(HubCrmNormalization.digits("12.345.678/0001-90")).isEqualTo("12345678000190");
        assertThat(HubCrmNormalization.normalizedText("São José S.A.")).isEqualTo("SAO JOSE S A");
        assertThat(HubCrmNormalization.shortName("BAUMER S.A.")).isEqualTo("BAUMER");
    }

    @Test
    void descartaContatoErickEEmailsOperacionaisInvalidos() {
        assertThat(HubCrmNormalization.validContactName("Erick")).isFalse();
        assertThat(HubCrmNormalization.validContactName("Fernanda Silva")).isTrue();
        assertThat(HubCrmNormalization.validEmail("ti@ti.com.br")).isFalse();
        assertThat(HubCrmNormalization.validEmail("compras@cliente.com.br")).isTrue();
    }

    @Test
    void catalogoPossuiOsDezMotivosAtivos() {
        assertThat(LossReason.values()).hasSize(10);
        assertThat(java.util.Arrays.stream(LossReason.values()).map(LossReason::arpaName))
                .contains("Preço alto", "Prazo e janela ruins", "Perda para concorrente");
    }
}
