package br.com.andreteixeira.agenterag.limite;

/**
 * Um dos limites do chat público foi atingido. {@link Assunto} diz de que limite se trata
 * (perguntas, ou tentativas de login erradas) e {@link Tipo}, qual: o total da demo ou o do IP
 * de quem tentou — o handler HTTP usa os dois para escolher a mensagem. A tentativa recusada não
 * conta em nenhum dos contadores.
 */
public class LimiteDiarioExcedidoException extends RuntimeException {

    public enum Tipo {
        TOTAL,
        IP
    }

    public enum Assunto {
        PERGUNTAS,
        LOGIN
    }

    private final Tipo tipo;
    private final Assunto assunto;

    public LimiteDiarioExcedidoException(Tipo tipo) {
        this(tipo, Assunto.PERGUNTAS);
    }

    public LimiteDiarioExcedidoException(Tipo tipo, Assunto assunto) {
        super("Limite atingido (" + assunto + "): " + tipo);
        this.tipo = tipo;
        this.assunto = assunto;
    }

    public Tipo tipo() {
        return tipo;
    }

    public Assunto assunto() {
        return assunto;
    }
}
