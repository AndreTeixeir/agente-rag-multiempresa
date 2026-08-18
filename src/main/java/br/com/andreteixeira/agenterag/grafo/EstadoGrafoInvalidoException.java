package br.com.andreteixeira.agenterag.grafo;

/**
 * Invariante interna do grafo violada — o {@code CompiledGraph} terminou sem
 * produzir o que {@link ConversaService} espera (estado final ausente, ou
 * presente mas sem {@code resposta}). Distinta de {@link IllegalStateException}
 * de propósito: essa última, lançada por {@link ConversaNodes#identificarEmpresa},
 * significa "o chamador esqueceu de mandar a empresa" (erro do cliente, HTTP
 * 400); esta aqui significa "o grafo quebrou uma garantia própria" (bug do
 * servidor, HTTP 500) — ver {@code controller.GlobalExceptionHandler}.
 * <p>
 * {@code public}: precisa ser referenciável do pacote {@code controller}
 * (outro pacote) pelo handler de exceções da Etapa 6.
 */
public class EstadoGrafoInvalidoException extends RuntimeException {

    public EstadoGrafoInvalidoException(String message) {
        super(message);
    }
}
