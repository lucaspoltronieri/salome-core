package br.com.salome.core.infrastructure.web.torre;

import br.com.salome.core.domain.torre.erro.AcessoNegado;
import br.com.salome.core.domain.torre.erro.RecursoNaoEncontrado;
import br.com.salome.core.domain.torre.erro.RegraViolada;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeia exceções de domínio da Torre para HTTP. Restringe-se aos controllers
 * de {@code ...web.torre} para não interferir nos painéis financeiros.
 */
@RestControllerAdvice(basePackages = "br.com.salome.core.infrastructure.web.torre")
@ConditionalOnProperty(prefix = "salome.torre", name = "enabled", havingValue = "true")
public class TorreExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontrado.class)
    public ResponseEntity<Map<String, String>> naoEncontrado(RecursoNaoEncontrado e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(RegraViolada.class)
    public ResponseEntity<Map<String, String>> regraViolada(RegraViolada e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(AcessoNegado.class)
    public ResponseEntity<Map<String, String>> acessoNegado(AcessoNegado e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("erro", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validacao(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(org.springframework.validation.FieldError::getDefaultMessage)
                .orElse("Requisição inválida.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("erro", msg));
    }
}
