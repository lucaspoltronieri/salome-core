package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.EventoAuditoria;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rastro de auditoria das ações sensíveis da Torre. Roda na mesma transação de
 * quem chama (ex.: cancelar atividade), então o evento só persiste se a ação
 * persistir.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
@Transactional("torreTransactionManager")
public class AuditoriaService {

    private static final int LIMITE = 200;

    private final AuditoriaRepository auditoriaRepository;
    private final Clock clock;

    public AuditoriaService(AuditoriaRepository auditoriaRepository, Clock clock) {
        this.auditoriaRepository = auditoriaRepository;
        this.clock = clock;
    }

    public void registrar(UsuarioAutenticado usuario, String acao, String entidade, Long idEntidade, String detalhe) {
        auditoriaRepository.registrar(new EventoAuditoria(
                0,
                usuario == null ? null : usuario.idFilial(),
                usuario == null ? null : usuario.id(),
                acao,
                entidade,
                idEntidade,
                detalhe,
                clock.instant()));
    }

    @Transactional(value = "torreTransactionManager", readOnly = true)
    public List<EventoAuditoria> listar(int idFilial) {
        return auditoriaRepository.listarPorFilial(idFilial, LIMITE);
    }
}
