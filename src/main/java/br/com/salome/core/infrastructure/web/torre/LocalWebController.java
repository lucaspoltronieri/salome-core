package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.LocalArmazemRepository;
import br.com.salome.core.domain.torre.LocalArmazem;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/locais")
public class LocalWebController {

    private final LocalArmazemRepository localRepository;

    public LocalWebController(LocalArmazemRepository localRepository) {
        this.localRepository = localRepository;
    }

    @GetMapping
    public List<LocalArmazem> listar(@RequestParam(required = false) Integer filial) {
        return localRepository.listarAtivos(AutenticacaoContexto.filialAtiva(filial));
    }
}
