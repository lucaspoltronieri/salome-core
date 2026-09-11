package br.com.salome.core.infrastructure.web.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmInactiveClientPdfService;
import br.com.salome.core.application.hubcrm.HubCrmMediaSigner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hub-crm/public/inativos")
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmInactiveClientPdfController {
    private final HubCrmMediaSigner signer;
    private final HubCrmInactiveClientPdfService pdf;

    public HubCrmInactiveClientPdfController(HubCrmMediaSigner signer, HubCrmInactiveClientPdfService pdf) {
        this.signer = signer;
        this.pdf = pdf;
    }

    @GetMapping(value = "/{clientId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable long clientId, @RequestParam int ano, @RequestParam long expires,
            @RequestParam String signature) {
        if (!signer.validInactiveClient(clientId, ano, expires, signature)) return ResponseEntity.status(403).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=transportes-" + clientId + "-" + ano + ".pdf")
                .cacheControl(CacheControl.noStore())
                .body(pdf.generate(clientId, ano));
    }
}
