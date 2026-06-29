package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.Atividade;
import br.com.salome.core.domain.torre.FilialTorre;
import br.com.salome.core.domain.torre.PerfilCodigo;
import br.com.salome.core.domain.torre.TipoAtividade;
import br.com.salome.core.domain.torre.auth.UsuarioAutenticado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Fechamento do dia: toda atividade aberta precisa ser encerrada no dia para o dia
 * ter resultado (horas-homem, tempo médio etc. só contam atividade FINALIZADA).
 * Roda no horário configurado e encerra o que ficou aberto. Descargas/carregamentos
 * são <b>concluídos</b> (efetivam o status final dos documentos marcados); o resto
 * é finalizado. Uma descarga incompleta libera a viagem para continuar no dia seguinte.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class FechamentoDiaService {

    private static final Logger log = LoggerFactory.getLogger(FechamentoDiaService.class);

    private final AtividadeRepository atividadeRepository;
    private final AtividadeService atividadeService;
    private final FilialTorreRepository filialRepository;

    public FechamentoDiaService(AtividadeRepository atividadeRepository,
                                AtividadeService atividadeService,
                                FilialTorreRepository filialRepository) {
        this.atividadeRepository = atividadeRepository;
        this.atividadeService = atividadeService;
        this.filialRepository = filialRepository;
    }

    /** Por padrão às 23:55 (horário de Brasília). Configurável via salome.torre.fechamento-cron. */
    @Scheduled(cron = "${salome.torre.fechamento-cron:0 55 23 * * *}", zone = "America/Sao_Paulo")
    public void fecharDia() {
        for (FilialTorre filial : filialRepository.listarAtivas()) {
            fecharFilial(filial.idFilial());
        }
    }

    /** Encerra todas as atividades abertas de uma filial. Público para acionamento manual/teste. */
    public int fecharFilial(int idFilial) {
        int fechadas = 0;
        for (Atividade a : atividadeRepository.listarAbertas(idFilial)) {
            try {
                Long resp = a.idResponsavel();
                UsuarioAutenticado ator = new UsuarioAutenticado(
                        resp == null ? 0L : resp, "FECHAMENTO_DIA", "fechamento", idFilial, PerfilCodigo.OPERADOR);
                boolean efetiva = resp != null
                        && (a.tipo() == TipoAtividade.DESCARGA_TRANSFERENCIA
                            || a.tipo() == TipoAtividade.CARREGAMENTO);
                if (efetiva) {
                    atividadeService.concluir(a.id(), idFilial, ator);
                } else {
                    atividadeService.finalizar(a.id(), idFilial, ator);
                }
                fechadas++;
            } catch (RuntimeException e) {
                log.warn("Fechamento do dia: falha ao encerrar atividade {} da filial {}: {}",
                        a.id(), idFilial, e.getMessage());
            }
        }
        if (fechadas > 0) {
            log.info("Fechamento do dia: {} atividade(s) encerrada(s) na filial {}.", fechadas, idFilial);
        }
        return fechadas;
    }
}
