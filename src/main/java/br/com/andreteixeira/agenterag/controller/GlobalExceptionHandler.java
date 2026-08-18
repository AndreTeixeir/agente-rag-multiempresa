package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.grafo.EstadoGrafoInvalidoException;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Mapeamento de exceção para HTTP da Etapa 6 (contrato decidido com o André,
 * relatório da etapa):
 * <ul>
 *     <li>{@link IllegalArgumentException} — slug de empresa inválido (chamador
 *     mandou algo, mas errado) — 400, mensagem original (escrita para ser lida).</li>
 *     <li>{@link IllegalStateException} — empresa ausente na 1ª mensagem
 *     (chamador esqueceu de mandar) — 400, mensagem original.</li>
 *     <li>{@link MethodArgumentNotValidException} — {@code threadId}/
 *     {@code mensagem} em branco — 400, mensagem do campo.</li>
 *     <li>{@link EstadoGrafoInvalidoException} — invariante interna do grafo
 *     quebrada — bug do servidor, não do cliente — 500, mensagem genérica no
 *     corpo (a original só vai para o log).</li>
 *     <li>{@link CompletionException} — o {@code CompiledGraph} do LangGraph4j
 *     roda os nós via {@code CompletableFuture} e embrulha QUALQUER exceção
 *     lançada dentro de um nó em {@code CompletionException -> GraphRunnerException
 *     -> ExecutionException -> causa real} (confirmado no log real ao testar a
 *     Etapa 6 pelo navegador/curl — o {@code @WebMvcTest} com serviço mockado
 *     não pega isso, porque o mock lança a exceção direto). Desce a cadeia de
 *     causas até achar um dos três tipos acima e delega para o handler certo;
 *     sem achar, cai no genérico.</li>
 *     <li>Qualquer outra {@link Exception} — 500, mesma regra de mensagem
 *     genérica + log.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERRO_INTERNO_GENERICO = "Erro interno ao processar a conversa.";

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(Map.of("erro", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidation(MethodArgumentNotValidException ex) {
        String mensagem = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("Requisição inválida.");
        return ResponseEntity.badRequest().body(Map.of("erro", mensagem));
    }

    @ExceptionHandler(EstadoGrafoInvalidoException.class)
    public ResponseEntity<Map<String, String>> handleEstadoGrafoInvalido(EstadoGrafoInvalidoException ex) {
        log.error("Invariante interna do grafo quebrada", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", ERRO_INTERNO_GENERICO));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletionException(CompletionException ex) {
        Throwable causaReal = causaRaiz(ex);
        if (causaReal instanceof IllegalArgumentException iae) {
            return handleIllegalArgument(iae);
        }
        if (causaReal instanceof IllegalStateException ise) {
            return handleIllegalState(ise);
        }
        if (causaReal instanceof EstadoGrafoInvalidoException egi) {
            return handleEstadoGrafoInvalido(egi);
        }
        return handleGeneric(ex);
    }

    private Throwable causaRaiz(Throwable t) {
        Throwable atual = t;
        while (atual.getCause() != null && atual.getCause() != atual) {
            atual = atual.getCause();
        }
        return atual;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Erro não mapeado ao processar requisição", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", ERRO_INTERNO_GENERICO));
    }
}
