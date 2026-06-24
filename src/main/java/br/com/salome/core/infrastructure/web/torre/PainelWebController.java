package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.PainelService;
import br.com.salome.core.domain.torre.PainelSnapshot;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Painel TV da Torre: página estática + snapshot JSON (público, atrás do nginx,
 * no mesmo espírito dos painéis financeiros).
 */
@Controller
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class PainelWebController {

    private final PainelService painelService;

    public PainelWebController(PainelService painelService) {
        this.painelService = painelService;
    }

    @GetMapping("/torre/painel/")
    public String pagina() {
        return "forward:/torre/painel/index.html";
    }

    @GetMapping("/api/torre/painel/snapshot")
    @ResponseBody
    public PainelSnapshot snapshot(@RequestParam int filial) {
        return painelService.snapshot(filial);
    }
}
