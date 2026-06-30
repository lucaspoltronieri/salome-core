package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.TipoVeiculo;
import br.com.salome.core.domain.torre.Veiculo;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro/consulta das placas de saída (Entrega/Transferência) por filial.
 * Cadastrar uma placa já existente apenas a reativa (idempotente).
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class VeiculoService {

    private final VeiculoRepository veiculoRepository;

    public VeiculoService(VeiculoRepository veiculoRepository) {
        this.veiculoRepository = veiculoRepository;
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<Veiculo> listar(int idFilial, TipoVeiculo tipo) {
        return veiculoRepository.listar(idFilial, tipo);
    }

    public Veiculo cadastrar(int idFilial, String placa, TipoVeiculo tipo) {
        String normalizada = normalizar(placa);
        if (normalizada.isBlank()) {
            throw new RegraViolada("Placa inválida.");
        }
        return veiculoRepository.buscarPorPlaca(idFilial, normalizada)
                .map(existente -> {
                    if (!existente.ativo()) {
                        veiculoRepository.definirAtivo(existente.id(), idFilial, true);
                    }
                    return new Veiculo(existente.id(), idFilial, normalizada, existente.tipo(), true);
                })
                .orElseGet(() -> {
                    Veiculo novo = new Veiculo(null, idFilial, normalizada, tipo, true);
                    long id = veiculoRepository.criar(novo);
                    return new Veiculo(id, idFilial, normalizada, tipo, true);
                });
    }

    private static String normalizar(String placa) {
        return placa == null ? "" : placa.trim().toUpperCase();
    }
}
