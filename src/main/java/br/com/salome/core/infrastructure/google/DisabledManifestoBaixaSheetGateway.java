package br.com.salome.core.infrastructure.google;

import br.com.salome.core.application.manifesto.ManifestoBaixaSheetGateway;
import br.com.salome.core.domain.manifesto.CteMapaSjpRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "salome.manifesto.export", name = "enabled", havingValue = "false",
        matchIfMissing = true)
public class DisabledManifestoBaixaSheetGateway implements ManifestoBaixaSheetGateway {

    @Override
    public void garantirEstrutura() {
    }

    @Override
    public void substituirMapaAtual(
            List<CteMapaSjpRecord> armazemSjp,
            List<CteMapaSjpRecord> emRotaEntrega,
            List<CteMapaSjpRecord> outrosArmazens,
            List<CteMapaSjpRecord> viagensParaSjp,
            Instant exportadoEm) {
    }
}
