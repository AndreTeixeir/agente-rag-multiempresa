package br.com.andreteixeira.agenterag.grafo;

/**
 * Como uma conversa é identificada no grafo (e em {@code LG4JThread.thread_name}): o id da conta
 * dona, dois pontos e o {@code threadId} que o navegador mandou. Uma conta nunca alcança a conversa
 * de outra, mesmo sabendo o {@code threadId} dela: o prefixo vem da sessão, não do corpo.
 * <p>
 * O id da conta é só dígitos (e nunca reaproveitado), então {@code <id>:<threadId>} de uma conta não
 * coincide com o de outra, qualquer que seja o {@code threadId}.
 */
public final class ChaveDaConversa {

    private ChaveDaConversa() {
    }

    public static String de(long contaId, String threadId) {
        return prefixoDaConta(contaId) + threadId;
    }

    /** Começo de todas as conversas da conta — para apagá-las com um {@code LIKE '<prefixo>%'}. */
    public static String prefixoDaConta(long contaId) {
        return contaId + ":";
    }
}
