package br.com.andreteixeira.agenterag.conta;

/**
 * O recurso não existe — ou existe, mas quem pediu não pode nem saber disso (uma conta de outra
 * empresa, o superadmin real): é a mesma resposta nos dois casos, 404. Ver {@code GlobalExceptionHandler}.
 */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
