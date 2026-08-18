package br.com.andreteixeira.agenterag.grafo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.springframework.stereotype.Service;

/**
 * Ponto de entrada do grafo — um turno por chamada. {@code threadId} é o UUID
 * gerado no front e enviado em toda requisição (mesmo texto do plano); é ele
 * que faz o {@link org.bsc.langgraph4j.checkpoint.PostgresSaver} achar o
 * checkpoint certo e continuar a mesma conversa.
 * <p>
 * {@code empresaEscolhida} só importa na primeira mensagem da conversa — nas
 * seguintes, o nó 1 ignora o valor porque a empresa já está fixa no estado
 * (ver {@link ConversaNodes#identificarEmpresa}). Pode passar {@code null}
 * depois da primeira mensagem sem problema.
 */
@Service
public class ConversaService {

    private final CompiledGraph<ConversaState> compiledGraph;

    public ConversaService(CompiledGraph<ConversaState> conversaCompiledGraph) {
        this.compiledGraph = conversaCompiledGraph;
    }

    public RespostaConversa responder(String threadId, String empresaEscolhida, String pergunta) {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put(ConversaState.PERGUNTA, pergunta);
        if (empresaEscolhida != null) {
            inputs.put(ConversaState.EMPRESA_ESCOLHIDA, empresaEscolhida);
        }

        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        ConversaState estadoFinal = compiledGraph
                .invoke(GraphInput.args(inputs), config)
                .orElseThrow(() -> new EstadoGrafoInvalidoException("Grafo não produziu estado final para threadId=" + threadId));

        String resposta = estadoFinal
                .resposta()
                .orElseThrow(() -> new EstadoGrafoInvalidoException("Estado final sem 'resposta' para threadId=" + threadId));

        return new RespostaConversa(resposta, estadoFinal.fontes(), estadoFinal.empresa().orElseThrow());
    }

    public record RespostaConversa(String texto, List<String> fontes, String empresa) {
    }
}
