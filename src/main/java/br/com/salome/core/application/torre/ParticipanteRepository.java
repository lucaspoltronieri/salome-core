package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Participante;
import java.time.Instant;
import java.util.List;

public interface ParticipanteRepository {

    /** Encerra qualquer participação ativa do usuário (regra: 1 ativa por vez). */
    int encerrarAtivasDoUsuario(long idUsuario, Instant em);

    long abrir(long idAtividade, long idUsuario, String funcao, String origem, Instant em);

    int encerrarNaAtividade(long idAtividade, long idUsuario, Instant em);

    int encerrarTodasDaAtividade(long idAtividade, Instant em);

    List<Participante> listarPorAtividade(long idAtividade);
}
