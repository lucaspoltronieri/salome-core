package br.com.salome.core.infrastructure.web.torre;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Shell do app web unificado da Torre (menu lateral + dashboard + telas). É um
 * SPA com roteamento por hash, então todas as rotas servem o mesmo
 * {@code index.html} — a navegação acontece no cliente via {@code location.hash}.
 * O shell é público ({@code /torre/**}); os dados (/api/torre/**) seguem exigindo JWT.
 */
@Controller
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreAppWebController {

    @GetMapping({"/torre", "/torre/", "/torre/app", "/torre/app/"})
    public String app() {
        return "forward:/torre/app/index.html";
    }
}
