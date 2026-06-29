package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.ArmazemService;
import br.com.salome.core.domain.torre.ArmazemSnapshot;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Armazém atual" — o que está fisicamente no galpão pelos estados próprios da
 * Torre, agrupado por box. Exige login (JWT); filial do token (ADMIN troca via
 * {@code filial}).
 */
@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class ArmazemWebController {

    private final ArmazemService armazemService;

    public ArmazemWebController(ArmazemService armazemService) {
        this.armazemService = armazemService;
    }

    @GetMapping("/api/torre/armazem/por-box")
    public ArmazemSnapshot porBox(@RequestParam(required = false) Integer filial) {
        return armazemService.snapshot(AutenticacaoContexto.filialAtiva(filial));
    }
}
