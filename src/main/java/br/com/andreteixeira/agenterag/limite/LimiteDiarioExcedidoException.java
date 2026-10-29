package br.com.andreteixeira.agenterag.limite;

/**
 * Um dos limites diários do chat público foi atingido. {@link Tipo} diz qual:
 * o total da demo ou o do IP de quem perguntou — o handler HTTP usa isso para
 * escolher a mensagem. A pergunta recusada não conta em nenhum dos contadores.
 */
public class LimiteDiarioExcedidoException extends RuntimeException {

    public enum Tipo {
        TOTAL,
        IP
    }

    private final Tipo tipo;

    public LimiteDiarioExcedidoException(Tipo tipo) {
        super("Limite diário de perguntas atingido: " + tipo);
        this.tipo = tipo;
    }

    public Tipo tipo() {
        return tipo;
    }
}
