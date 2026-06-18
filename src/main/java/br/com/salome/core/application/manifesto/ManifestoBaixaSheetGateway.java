package br.com.salome.core.application.manifesto;

import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import java.time.Instant;
import java.util.List;

public interface ManifestoBaixaSheetGateway {

    void garantirEstrutura();

    void substituirMapaAtual(
            List<CteMapaSjpRecord> armazemSjp,
            List<CteMapaSjpRecord> emRotaEntrega,
            List<CteMapaSjpRecord> outrosArmazens,
            List<CteMapaSjpRecord> viagensParaSjp,
            Instant exportadoEm);
}
