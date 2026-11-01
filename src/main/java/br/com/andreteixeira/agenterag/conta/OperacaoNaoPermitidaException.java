package br.com.andreteixeira.agenterag.conta;

/**
 * O papel de quem pediu não permite esta operação sobre este recurso: 403, com a mensagem do caso.
 * Existe no lugar do {@code AccessDeniedException} do Spring Security porque o
 * {@code @ExceptionHandler(Exception.class)} do {@code GlobalExceptionHandler} engoliria este último
 * e responderia 500. Ver {@code GlobalExceptionHandler}.
 */
public class OperacaoNaoPermitidaException extends RuntimeException {

    public OperacaoNaoPermitidaException(String mensagem) {
        super(mensagem);
    }
}
