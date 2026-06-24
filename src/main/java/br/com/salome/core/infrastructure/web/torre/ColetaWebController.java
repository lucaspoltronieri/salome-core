package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.ColetaService;
import br.com.salome.core.domain.torre.BiparNfRequest;
import br.com.salome.core.domain.torre.CasamentoResultado;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class ColetaWebController {

    private final ColetaService coletaService;

    public ColetaWebController(ColetaService coletaService) {
        this.coletaService = coletaService;
    }

    /** Bipa uma NF na descarga de coleta (pré-CTe). */
    @PostMapping("/api/torre/atividades/{id}/coletas")
    public DocumentoOperacional biparNf(@PathVariable long id,
                                        @Valid @RequestBody BiparNfRequest req,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return coletaService.biparNf(id, req, usuario);
    }

    /** Casa os pré-CTes pendentes com CT-es já emitidos. */
    @PostMapping("/api/torre/coletas/casar")
    public CasamentoResultado casar(@RequestParam(required = false) Integer filial) {
        return coletaService.casarPendentes(AutenticacaoContexto.filialAtiva(filial));
    }

    /** Fila de pendência: pré-CTes ainda não casados. */
    @GetMapping("/api/torre/coletas/pendentes")
    public List<DocumentoOperacional> pendentes(@RequestParam(required = false) Integer filial) {
        return coletaService.listarPendentes(AutenticacaoContexto.filialAtiva(filial));
    }
}
