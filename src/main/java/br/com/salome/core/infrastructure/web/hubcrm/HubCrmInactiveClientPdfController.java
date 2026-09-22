package br.com.salome.core.infrastructure.web.hubcrm;

import br.com.salome.core.application.hubcrm.HubCrmInactiveClientPdfService;
import br.com.salome.core.application.hubcrm.HubCrmMediaSigner;
import br.com.salome.core.application.hubcrm.InactiveClientRepository;
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
@RequestMapping("/api/hub-crm/public")
@ConditionalOnProperty(prefix = "salome.hub-crm", name = "enabled", havingValue = "true")
public class HubCrmInactiveClientPdfController {
    private final HubCrmMediaSigner signer;
    private final HubCrmInactiveClientPdfService pdf;
    private final InactiveClientRepository repository;

    public HubCrmInactiveClientPdfController(HubCrmMediaSigner signer, HubCrmInactiveClientPdfService pdf,
            InactiveClientRepository repository) {
        this.signer = signer;
        this.pdf = pdf;
        this.repository = repository;
    }

    @GetMapping(value = "/inativos/{clientId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable long clientId, @RequestParam int ano, @RequestParam long expires,
            @RequestParam String signature) {
        if (!signer.validInactiveClient(clientId, ano, expires, signature)) return ResponseEntity.status(403).build();
        return inline("transportes-" + clientId + "-" + ano + ".pdf", pdf.generate(clientId, ano));
    }

    // Gerado na hora: a relação sempre traz os CT-es recebidos até o momento em que o link é aberto.
    @GetMapping(value = "/nao-pagantes/{clientId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> receivedPdf(@PathVariable long clientId, @RequestParam long expires,
            @RequestParam String signature) {
        if (!signer.validReceivedClient(clientId, expires, signature)) return ResponseEntity.status(403).build();
        return repository.findReceivedReport(clientId)
                .map(report -> inline("transportes-recebidos-" + clientId + ".pdf", pdf.generate(report)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<byte[]> inline(String fileName, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=" + fileName)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
