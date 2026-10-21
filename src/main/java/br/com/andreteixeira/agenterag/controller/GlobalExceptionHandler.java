package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.grafo.EstadoGrafoInvalidoException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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
 *     causas até achar um dos tipos acima, ou um {@link LangChain4jException}
 *     do provedor do modelo, e delega para o handler certo; sem achar, cai no
 *     genérico.</li>
 *     <li>{@link LangChain4jException} — falha na chamada ao provedor do modelo
 *     de chat (confirmado no fonte da LangChain4j 1.18.1: o cliente HTTP
 *     traduz o status em exceção). Limite de requisições (429,
 *     {@link RateLimitException}), saldo ou pagamento (402, que a biblioteca
 *     não mapeia: chega como {@code InvalidRequestException} com um
 *     {@link HttpException} 402 na causa) e tempo limite
 *     ({@link TimeoutException}) são indisponibilidade temporária do serviço —
 *     503, com mensagem clara. Chave inválida (401) e qualquer outro erro do
 *     provedor — 500, mensagem genérica no corpo e o detalhe só no log. O 503
 *     é deliberado, para não se confundir com o 429 do limite diário da
 *     própria demo.</li>
 *     <li>{@link NoResourceFoundException} — 404 de recurso estático sem
 *     handler (ex.: {@code robots.txt}, sondas de DevTools) — 404 silencioso
 *     no corpo, com log em {@code DEBUG} (não {@code ERROR}) para não perder
 *     rastreabilidade caso um recurso legítimo suma num deploy. Nunca é
 *     lançada de dentro de um nó do grafo, então nunca passa pela cadeia de
 *     desembrulho de {@link CompletionException} acima.</li>
 *     <li>Qualquer outra {@link Exception} — 500, mesma regra de mensagem
 *     genérica + log.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERRO_INTERNO_GENERICO = "Erro interno ao processar a conversa.";
    private static final String LIMITE_DE_USO_DO_PROVEDOR =
            "O assistente atingiu o limite de uso no momento. Tente novamente mais tarde.";
    private static final String TEMPO_LIMITE_DO_PROVEDOR = "O assistente demorou demais para responder. Tente novamente.";
    private static final int HTTP_PAGAMENTO_NECESSARIO = 402;

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

    @ExceptionHandler(LangChain4jException.class)
    public ResponseEntity<Map<String, String>> handleErroDoProvedor(LangChain4jException ex) {
        if (ex instanceof RateLimitException || statusHttpDoProvedor(ex) == HTTP_PAGAMENTO_NECESSARIO) {
            log.warn("Provedor do modelo recusou por limite de uso ou saldo: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", LIMITE_DE_USO_DO_PROVEDOR));
        }
        if (ex instanceof TimeoutException) {
            log.warn("Provedor do modelo não respondeu dentro do tempo limite: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("erro", TEMPO_LIMITE_DO_PROVEDOR));
        }
        log.error("Erro do provedor do modelo", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", ERRO_INTERNO_GENERICO));
    }

    @ExceptionHandler(CompletionException.class)
    public ResponseEntity<Map<String, String>> handleCompletionException(CompletionException ex) {
        List<Throwable> cadeia = cadeiaDeCausas(ex);
        // O erro do provedor vale em qualquer ponto da cadeia — a causa raiz dele é o
        // HttpException ou o HttpTimeoutException do cliente HTTP, não a exceção que
        // identifica o caso.
        for (Throwable causa : cadeia) {
            if (causa instanceof LangChain4jException erroDoProvedor) {
                return handleErroDoProvedor(erroDoProvedor);
            }
        }
        Throwable causaReal = cadeia.get(cadeia.size() - 1);
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

    /** Da própria exceção até a causa raiz; a guarda por identidade evita laço numa cadeia circular. */
    private static List<Throwable> cadeiaDeCausas(Throwable inicio) {
        List<Throwable> cadeia = new ArrayList<>();
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable atual = inicio; atual != null && vistos.add(atual); atual = atual.getCause()) {
            cadeia.add(atual);
        }
        return cadeia;
    }

    /** Status HTTP devolvido pelo provedor, se houver um {@link HttpException} na cadeia; senão, -1. */
    private static int statusHttpDoProvedor(Throwable ex) {
        for (Throwable causa : cadeiaDeCausas(ex)) {
            if (causa instanceof HttpException http) {
                return http.statusCode();
            }
        }
        return -1;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Recurso estático não encontrado: {}", ex.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Erro não mapeado ao processar requisição", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("erro", ERRO_INTERNO_GENERICO));
    }
}
