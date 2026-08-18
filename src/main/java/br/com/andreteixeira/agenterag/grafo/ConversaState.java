package br.com.andreteixeira.agenterag.grafo;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bsc.langgraph4j.state.AgentState;

/**
 * Estado da conversa — empresa selecionada + histórico (janela deslizante),
 * como pedido pelo plano, mais os campos de passagem de uma execução do grafo
 * (pergunta do turno atual, pergunta reescrita, resposta, fontes).
 * <p>
 * Chaves sem canal registrado em {@link ConversaGraphConfig} (todas exceto
 * {@link #HISTORICO}) usam o comportamento padrão do LangGraph4j: o valor mais
 * recente sempre vence — é exatamente "última pergunta/resposta do turno", não
 * precisa de reducer.
 */
public class ConversaState extends AgentState {

    public static final String EMPRESA = "empresa";
    public static final String EMPRESA_ESCOLHIDA = "empresaEscolhida";
    public static final String HISTORICO = "historico";
    public static final String PERGUNTA = "pergunta";
    public static final String PERGUNTA_EFETIVA = "perguntaEfetiva";
    public static final String RESPOSTA = "resposta";
    public static final String FONTES = "fontes";

    public ConversaState(Map<String, Object> initData) {
        super(initData);
    }

    /** Empresa já fixada na sessão — vazio só antes do nó 1 rodar pela primeira vez. */
    public Optional<String> empresa() {
        return value(EMPRESA);
    }

    /** Empresa informada pelo chamador nesta chamada — só é usada se {@link #empresa()} ainda estiver vazio. */
    public Optional<String> empresaEscolhida() {
        return value(EMPRESA_ESCOLHIDA);
    }

    @SuppressWarnings("unchecked")
    public List<Turno> historico() {
        return this.<List<Turno>>value(HISTORICO).orElseGet(List::of);
    }

    public String pergunta() {
        return this.<String>value(PERGUNTA)
                .orElseThrow(() -> new IllegalStateException("Estado sem 'pergunta' — entrada obrigatória em toda chamada."));
    }

    /** Pergunta efetiva para busca: a reescrita, se o nó de reescrita rodou; senão a original. */
    public String perguntaEfetiva() {
        return this.<String>value(PERGUNTA_EFETIVA).orElseGet(this::pergunta);
    }

    public Optional<String> resposta() {
        return value(RESPOSTA);
    }

    @SuppressWarnings("unchecked")
    public List<String> fontes() {
        return this.<List<String>>value(FONTES).orElseGet(List::of);
    }
}
