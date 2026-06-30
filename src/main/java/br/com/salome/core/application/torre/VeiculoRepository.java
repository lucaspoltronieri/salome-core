package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.TipoVeiculo;
import br.com.salome.core.domain.torre.Veiculo;
import java.util.List;
import java.util.Optional;

public interface VeiculoRepository {

    /** Placas ativas da filial para um tipo (Entrega/Transferência). */
    List<Veiculo> listar(int idFilial, TipoVeiculo tipo);

    Optional<Veiculo> buscarPorPlaca(int idFilial, String placa);

    long criar(Veiculo veiculo);

    /** Reativa uma placa existente (ex.: cadastrar uma que estava inativa). */
    boolean definirAtivo(long id, int idFilial, boolean ativo);
}
