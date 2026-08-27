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
    void removeIdentificadorNumericoDeEmpresarioIndividual() {
        assertThat(HubCrmNormalization.businessName("63.110.705 REYNALDO LUIZ CERQUEIRA DE SOUZA"))
                .isEqualTo("REYNALDO LUIZ CERQUEIRA DE SOUZA");
        assertThat(HubCrmNormalization.shortName("63.110.705 REYNALDO LUIZ CERQUEIRA DE SOUZA"))
                .isEqualTo("REYNALDO LUIZ CERQUEIRA");
        assertThat(HubCrmNormalization.businessName("123.456.789-00 MARIA DA SILVA"))
                .isEqualTo("MARIA DA SILVA");
        assertThat(HubCrmNormalization.businessName("12.345.678/0001-90 JOAO COMERCIO"))
                .isEqualTo("JOAO COMERCIO");
    }

    @Test
    void preservaNomeComercialAlfanumericoOuNumeroCurto() {
        assertThat(HubCrmNormalization.businessName("3M DO BRASIL LTDA"))
                .isEqualTo("3M DO BRASIL LTDA");
        assertThat(HubCrmNormalization.businessName("1001 FESTAS LTDA"))
                .isEqualTo("1001 FESTAS LTDA");
    }

    @Test
    void descartaContatoErickEEmailsOperacionaisInvalidos() {
        assertThat(HubCrmNormalization.validContactName("Erick")).isFalse();
        assertThat(HubCrmNormalization.validContactName("Fernanda Silva")).isTrue();
        assertThat(HubCrmNormalization.contactName("1111111111 || VANIA")).isEqualTo("VANIA");
        assertThat(HubCrmNormalization.validContactName("1111111111 || VANIA")).isTrue();
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
