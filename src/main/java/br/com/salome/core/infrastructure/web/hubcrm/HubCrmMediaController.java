package br.com.salome.core.infrastructure.web.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmMediaSigner;
import br.com.salome.core.application.hubcrm.HubCrmQuotePdfService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hub-crm/public/cotacoes")
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmMediaController {
    private final HubCrmMediaSigner signer;
    private final HubCrmQuotePdfService pdf;

    public HubCrmMediaController(HubCrmMediaSigner signer, HubCrmQuotePdfService pdf) {
        this.signer = signer;
        this.pdf = pdf;
    }

    @GetMapping(value = "/{quoteId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable long quoteId, @RequestParam long expires,
            @RequestParam String signature) {
        if (!signer.valid(quoteId, expires, signature)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=cotacao-" + quoteId + ".pdf")
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(pdf.generate(quoteId));
    }
}
