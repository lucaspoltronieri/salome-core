package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.application.torre.VeiculoService;
import br.com.salome.core.domain.torre.CadastrarVeiculoRequest;
import br.com.salome.core.domain.torre.TipoVeiculo;
import br.com.salome.core.domain.torre.Veiculo;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import br.com.salome.core.infrastructure.torre.auth.AutenticacaoContexto;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@RequestMapping("/api/torre/veiculos")
public class VeiculoWebController {

    private final VeiculoService veiculoService;

    public VeiculoWebController(VeiculoService veiculoService) {
        this.veiculoService = veiculoService;
    }

    /** Placas da filial para um tipo de carregamento (ENTREGA|TRANSFERENCIA). */
    @GetMapping
    public List<Veiculo> listar(@RequestParam String tipo,
                                @RequestParam(required = false) Integer filial) {
        TipoVeiculo t = TipoVeiculo.porCodigo(tipo)
                .orElseThrow(() -> new RegraViolada("Tipo de veículo inválido: use ENTREGA ou TRANSFERENCIA."));
        return veiculoService.listar(AutenticacaoContexto.filialAtiva(filial), t);
    }

    /** Cadastra (ou reativa) uma placa na filial do usuário. */
    @PostMapping
    public Veiculo cadastrar(@Valid @RequestBody CadastrarVeiculoRequest req,
                             @RequestParam(required = false) Integer filial) {
        return veiculoService.cadastrar(AutenticacaoContexto.filialAtiva(filial), req.placa(), req.tipo());
    }
}
