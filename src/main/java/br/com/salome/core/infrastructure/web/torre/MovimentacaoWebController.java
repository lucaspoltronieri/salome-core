package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.MovimentacaoService;
import br.com.salome.core.domain.torre.DocumentoOperacional;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import jakarta.validation.constraints.NotNull;
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
public class MovimentacaoWebController {

    private final MovimentacaoService movimentacaoService;

    public MovimentacaoWebController(MovimentacaoService movimentacaoService) {
        this.movimentacaoService = movimentacaoService;
    }

    /** Documentos disponíveis para separar (NO_ARMAZEM) ou carregar (NO_ARMAZEM + SEPARADO_BOX). */
    @GetMapping("/api/torre/documentos/disponiveis")
    public List<DocumentoOperacional> disponiveis(@RequestParam String para,
                                                  @RequestParam(required = false) Integer filial) {
        int idFilial = AutenticacaoContexto.filialAtiva(filial);
        return "carregar".equalsIgnoreCase(para)
                ? movimentacaoService.disponiveisParaCarregar(idFilial)
                : movimentacaoService.disponiveisParaSeparar(idFilial);
    }

    @PostMapping("/api/torre/atividades/{id}/separar")
    public DocumentoOperacional separar(@PathVariable long id,
                                        @RequestBody SepararRequest corpo,
                                        @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return movimentacaoService.separar(id, corpo.idDocumento(), corpo.idLocal(), usuario);
    }

    /** Modo rápido: separa vários documentos para o mesmo box de uma vez (ex.: Distribuição). */
    @PostMapping("/api/torre/atividades/{id}/separar/lote")
    public List<DocumentoOperacional> separarLote(@PathVariable long id,
                                                  @RequestBody SepararLoteRequest corpo,
                                                  @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return movimentacaoService.separarLote(id, corpo.idsDocumento(), corpo.idLocal(), usuario);
    }

    @PostMapping("/api/torre/atividades/{id}/carregar")
    public DocumentoOperacional carregar(@PathVariable long id,
                                         @RequestBody CarregarRequest corpo,
                                         @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return movimentacaoService.carregar(id, corpo.idDocumento(), usuario);
    }

    /** Modo rápido: marca vários documentos para carregamento de uma vez. */
    @PostMapping("/api/torre/atividades/{id}/carregar/lote")
    public List<DocumentoOperacional> carregarLote(@PathVariable long id,
                                                   @RequestBody CarregarLoteRequest corpo,
                                                   @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return movimentacaoService.carregarLote(id, corpo.idsDocumento(), usuario);
    }

    public record SepararRequest(@NotNull Long idDocumento, @NotNull Long idLocal) {
    }

    public record CarregarRequest(@NotNull Long idDocumento) {
    }

    public record CarregarLoteRequest(@NotNull List<Long> idsDocumento) {
    }

    public record SepararLoteRequest(@NotNull List<Long> idsDocumento, @NotNull Long idLocal) {
    }
}
