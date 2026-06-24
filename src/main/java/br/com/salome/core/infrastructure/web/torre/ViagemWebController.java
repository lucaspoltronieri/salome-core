package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.ViagemAguardandoService;
import br.com.salome.core.domain.torre.ViagemAguardando;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/viagens")
public class ViagemWebController {

    private final ViagemAguardandoService viagemAguardandoService;

    public ViagemWebController(ViagemAguardandoService viagemAguardandoService) {
        this.viagemAguardandoService = viagemAguardandoService;
    }

    @GetMapping("/aguardando-descarga")
    public List<ViagemAguardando> aguardandoDescarga(@RequestParam(required = false) Integer filial) {
        return viagemAguardandoService.listar(AutenticacaoContexto.filialAtiva(filial));
    }
}
