package br.com.andreteixeira.agenterag.grafo;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.bsc.langgraph4j.CompileConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.checkpoint.PostgresSaver;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;
import org.bsc.langgraph4j.state.Reducer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring do grafo da Etapa 5 — isolado do resto (ingestão, resposta) na
 * mesma convenção de {@code RespostaConfig}: responsabilidade própria, não
 * uma terceira porta.
 * <p>
 * <b>Achado que decidiu a configuração abaixo</b> (lendo o fonte da
 * {@code 1.9.0-beta2}, não a documentação): {@link CompileConfig} tem
 * {@code releaseThread() == true} por padrão, e {@code CompiledGraph.Emitter}
 * libera a thread automaticamente (marca {@code is_released = true} na tabela
 * {@code LG4JThread}) toda vez que o grafo chega no {@code END} sem
 * interrupção. Sem {@code .releaseThread(false)} explícito aqui, CADA
 * conversa se "encerraria" sozinha ao fim do primeiro turno — o segundo turno
 * não encontraria checkpoint nenhum (a consulta do
 * {@code PostgresSaver.loadCheckpoints} filtra {@code is_released = FALSE}) e
 * o histórico simplesmente não persistiria entre chamadas. Esse é
 * comportamento correto para um caso de uso "grafo com pausas/retomadas"
 * (o desenho original da lib), mas errado para "chat multi-turno onde cada
 * mensagem é uma invocação independente do grafo" — o nosso caso.
 * <p>
 * As tabelas de checkpoint ({@code LG4JThread}, {@code LG4JCheckpoint}) vêm
 * da migração Flyway {@code V3__grafo_checkpoints.sql}, nunca de criação
 * automática — por isso {@code .createTables(false)} está explícito abaixo,
 * mesmo sendo o default: documenta a decisão, não deixa implícito.
 */
@Configuration
public class ConversaGraphConfig {

    /** Tamanho da janela deslizante do histórico — últimos N turnos, não a conversa inteira. */
    private static final int MAX_TURNOS_HISTORICO = 6;

    @Bean
    public StateGraph<ConversaState> conversaStateGraph(ConversaNodes nodes) throws GraphStateException {
        Map<String, Channel<?>> channels = Map.of(ConversaState.HISTORICO, Channels.base(janelaDeslizanteReducer(), ArrayList::new));

        StateGraph<ConversaState> stateGraph = new StateGraph<>(channels, ConversaState::new);

        stateGraph.addNode("identificar_empresa", AsyncNodeAction.node_async(nodes::identificarEmpresa));
        stateGraph.addNode("reescrever_consulta", AsyncNodeAction.node_async(nodes::reescreverConsulta));
        stateGraph.addNode("responder", AsyncNodeAction.node_async(nodes::responder));

        stateGraph.addEdge(StateGraph.START, "identificar_empresa");
        stateGraph.addConditionalEdges(
                "identificar_empresa",
                AsyncEdgeAction.edge_async(nodes::decidirSeReescreve),
                Map.of(
                        "com_historico", "reescrever_consulta",
                        "sem_historico", "responder"));
        stateGraph.addEdge("reescrever_consulta", "responder");
        stateGraph.addEdge("responder", StateGraph.END);

        return stateGraph;
    }

    @Bean
    public PostgresSaver postgresSaver(DataSource dataSource, StateGraph<ConversaState> conversaStateGraph) throws SQLException {
        return PostgresSaver.builder()
                .datasource(dataSource)
                .stateSerializer(conversaStateGraph.getStateSerializer())
                // As tabelas vêm de V3__grafo_checkpoints.sql — nunca criação automática.
                .createTables(false)
                .build();
    }

    @Bean
    public CompiledGraph<ConversaState> conversaCompiledGraph(StateGraph<ConversaState> conversaStateGraph, PostgresSaver postgresSaver)
            throws GraphStateException {
        CompileConfig compileConfig = CompileConfig.builder()
                .checkpointSaver(postgresSaver)
                // Ver javadoc da classe — sem isso, o histórico não sobrevive entre turnos.
                .releaseThread(false)
                .build();
        return conversaStateGraph.compile(compileConfig);
    }

    private static Reducer<List<Turno>> janelaDeslizanteReducer() {
        return (antigo, novo) -> {
            List<Turno> combinado = new ArrayList<>(antigo == null ? List.of() : antigo);
            combinado.addAll(novo);
            int inicio = Math.max(0, combinado.size() - MAX_TURNOS_HISTORICO);
            return List.copyOf(combinado.subList(inicio, combinado.size()));
        };
    }
}
