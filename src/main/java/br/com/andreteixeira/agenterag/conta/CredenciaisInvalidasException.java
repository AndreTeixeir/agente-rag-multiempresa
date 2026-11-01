package br.com.andreteixeira.agenterag.conta;

/**
 * Usuário ou senha não conferem. É a mesma exceção, com a mesma mensagem, para "o usuário não
 * existe" e para "a senha está errada": quem erra o login não descobre qual dos dois falhou
 * ({@code GlobalExceptionHandler} devolve 401).
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("Usuário ou senha inválidos.");
    }
}
