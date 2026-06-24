package br.com.salome.core.application.torre;

import br.com.salome.core.domain.torre.erro.RegraViolada;
import br.com.salome.core.infrastructure.torre.TorreProperties;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Guarda fotos de ocorrência no disco. O caminho é gerado pelo servidor
 * ({@code <ano>/<mes>/<filial>/<random>.<ext>}) — o cliente nunca define o path,
 * evitando path traversal. {@code foto_path} guarda só o caminho relativo.
 */
@Service
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class FotoStorageService {

    private static final Set<String> EXTENSOES = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter PASTA = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path raiz;
    private final Clock clock;

    public FotoStorageService(TorreProperties properties, Clock clock) {
        this.raiz = Path.of(properties.fotos().effectiveDir()).toAbsolutePath().normalize();
        this.clock = clock;
    }

    /** Salva a foto e devolve o caminho relativo a guardar em {@code foto_path}. */
    public String salvar(MultipartFile foto, int idFilial) {
        if (foto == null || foto.isEmpty()) {
            throw new RegraViolada("Arquivo de foto vazio.");
        }
        String ext = extensao(foto);
        String relativo = PASTA.format(clock.instant().atZone(zona())) + "/" + idFilial + "/"
                + java.util.UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path destino = raiz.resolve(relativo).normalize();
        if (!destino.startsWith(raiz)) {
            throw new RegraViolada("Caminho de foto inválido.");
        }
        try {
            Files.createDirectories(destino.getParent());
            try (var in = foto.getInputStream()) {
                Files.copy(in, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar foto da ocorrência.", e);
        }
        return relativo;
    }

    /** Resolve o caminho absoluto de uma foto já salva, validando contra traversal. */
    public Path resolver(String relativo) {
        if (relativo == null || relativo.isBlank()) {
            throw new RegraViolada("Ocorrência sem foto.");
        }
        Path destino = raiz.resolve(relativo).normalize();
        if (!destino.startsWith(raiz) || !Files.exists(destino)) {
            throw new br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado("Foto não encontrada.");
        }
        return destino;
    }

    private String extensao(MultipartFile foto) {
        String nome = foto.getOriginalFilename();
        String ext = nome == null || !nome.contains(".")
                ? ""
                : nome.substring(nome.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!EXTENSOES.contains(ext)) {
            throw new RegraViolada("Formato de foto não suportado (use JPG, PNG ou WEBP).");
        }
        return ext.equals("jpeg") ? "jpg" : ext;
    }

    private ZoneId zona() {
        return clock.getZone();
    }
}
