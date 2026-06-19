package br.com.salome.core.infrastructure.web;

import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expoe a versao da aplicacao (lida do pom.xml via build-info do Spring Boot)
 * para que qualquer tela ou consulta saiba exatamente o que esta no ar.
 */
@RestController
public class VersaoWebController {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Nullable
    private final BuildProperties buildProperties;

    public VersaoWebController(@Nullable BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/api/versao")
    @ResponseBody
    public Map<String, Object> versao() {
        Map<String, Object> resposta = new LinkedHashMap<>();
        if (buildProperties == null) {
            // Build sem build-info (ex.: execucao direta na IDE sem o goal Maven).
            resposta.put("versao", "dev");
            resposta.put("nome", "salome-core");
            return resposta;
        }
        resposta.put("versao", buildProperties.getVersion());
        resposta.put("nome", buildProperties.getName());
        if (buildProperties.getTime() != null) {
            resposta.put("compiladoEm", buildProperties.getTime().atZone(FUSO).toLocalDateTime().toString());
        }
        return resposta;
    }
}
