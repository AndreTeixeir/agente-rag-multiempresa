package br.com.andreteixeira.agenterag.grafo;

import br.com.andreteixeira.agenterag.resposta.RespostaService;
import dev.langchain4j.model.chat.ChatModel;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * As ações dos 3 nós do grafo (mais a decisão da aresta condicional). Isolado
 * do wiring do grafo ({@link ConversaGraphConfig}) para que cada método fique
 * um método Java comum, testável sem precisar montar o {@code StateGraph}.
 * <p>
 * Nós 3 (recuperação), 4 (avaliação de limiar) e 5 (resposta/admissão de
 * ausência) do plano viram UM nó de grafo só ({@link #responder}), porque é
 * exatamente assim que {@link RespostaService#responder} já os implementa —
 * as duas comportas (busca vazia → admissão sem custo de LLM; prompt de
 * sistema → admissão mesmo com contexto presente) já vivem lá, inalteradas.
 * Separar isso em 3 nós de grafo exigiria quebrar {@code RespostaService} em
 * 3 métodos encadeados só para bater com a contagem de nós do plano — troca
 * de uma reafirmação textual por reimplementação de lógica que já funciona e
 * já foi validada na Etapa 4.2b. Não fiz essa troca.
 */
@Component
class ConversaNodes {

    private static final Logger log = LoggerFactory.getLogger(ConversaNodes.class);

    /**
     * Empresas conhecidas pelo sistema — os mesmos três valores que
     * {@code IngestionService.PREFIXO_EMPRESA} grava no metadado
     * {@code empresa} de cada chunk (conferido lendo aquele código-fonte,
     * achado #1 da revisão de diff da Etapa 5,
     * {@code 2026-08-18-etapa-5-revisao-diff.md}).
     * <p>
     * <b>Isso DUPLICA aquela lista</b> — {@code PREFIXO_EMPRESA} é
     * {@code private} em {@code IngestionService}, não reutilizável daqui sem
     * acoplar o pacote {@code grafo} ao pacote {@code ingestao} só por causa
     * de uma constante. Se uma quarta empresa for adicionada ao sistema,
     * **este {@code Set} também precisa ser atualizado manualmente** — o
     * build não avisa se um dos dois lugares for esquecido.
     * <p>
     * {@code LinkedHashSet}, não {@code Set.of(...)}: a ordem de iteração de
     * {@code Set.of(...)} não é estável entre execuções da JVM (varia por
     * segurança desde o Java 9), o que deixaria a mensagem de erro abaixo com
     * uma ordem diferente a cada vez — ruim para ler log e para escrever um
     * teste que compare o texto da exceção.
     */
    private static final Set<String> EMPRESAS_VALIDAS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of("bimbam", "mercado-central", "santo-pegasus")));

    private static final String PROMPT_REESCRITA =
            """
            Histórico da conversa até agora:
            %s

            Nova pergunta do usuário (pode depender do histórico acima para fazer sentido): "%s"

            Reescreva a nova pergunta como uma pergunta autossuficiente, sem depender do
            histórico da conversa, mantendo a intenção original do usuário. Responda
            SOMENTE com a pergunta reescrita — sem comentários, sem aspas, sem prefixo.
            """;

    private final RespostaService respostaService;
    private final ChatModel chatModel;

    ConversaNodes(RespostaService respostaService, ChatModel chatModel) {
        this.respostaService = respostaService;
        this.chatModel = chatModel;
    }

    /**
     * Nó 1 — identificação de empresa (se ainda não definida no estado).
     * Não adivinha empresa (CLAUDE.md) — exige que o chamador informe
     * explicitamente na primeira mensagem da conversa, e que o valor
     * informado seja uma das {@link #EMPRESAS_VALIDAS}: {@code trim}/
     * {@code lowercase} NÃO são aplicados de propósito — os três valores são
     * identificadores internos (slugs), não texto digitado por humano; a
     * Etapa 6 precisa mandar exatamente o slug (não o nome de exibição, ex.
     * "BimBam Buy"), e falhar alto e exato aqui força isso a ficar correto em
     * vez de mascarado por uma normalização que só cobriria a fatia estreita
     * de erro de maiúscula/espaço. Uma vez fixada, ignora qualquer novo valor
     * recebido: trocar de empresa exige nova conversa (thread ID novo), não é
     * possível dentro da mesma.
     */
    Map<String, Object> identificarEmpresa(ConversaState state) {
        if (state.empresa().isPresent()) {
            log.debug("Empresa já fixa nesta sessão: {} — ignorando qualquer novo valor recebido.", state.empresa().get());
            return Map.of();
        }
        String empresaEscolhida = state.empresaEscolhida()
                .orElseThrow(() -> new IllegalStateException(
                        "Primeira mensagem da conversa precisa informar a empresa — o sistema não adivinha empresa."));

        if (!EMPRESAS_VALIDAS.contains(empresaEscolhida)) {
            // IllegalArgumentException, deliberadamente diferente da IllegalStateException
            // acima: aqui o chamador MANDOU um valor, só que inválido (o valor existe, é
            // errado) — semântica distinta de "faltou mandar" (estado ausente). Dá pra Etapa
            // 6 mapear os dois tipos de exceção para respostas HTTP diferentes se quiser.
            throw new IllegalArgumentException(
                    "Empresa '%s' não reconhecida. Valores aceitos: %s".formatted(empresaEscolhida, EMPRESAS_VALIDAS));
        }

        log.info("Empresa fixada nesta sessão: {}", empresaEscolhida);
        return Map.of(ConversaState.EMPRESA, empresaEscolhida);
    }

    /**
     * Decisão da aresta condicional após o nó 1 — só passa pelo nó de
     * reescrita quando há histórico; sem histórico, não há o que reescrever
     * (a pergunta já é autossuficiente por ser a primeira da conversa).
     */
    String decidirSeReescreve(ConversaState state) {
        return state.historico().isEmpty() ? "sem_historico" : "com_historico";
    }

    /** Nó 2 — reescrita de consulta, só alcançado quando há histórico (aresta condicional). */
    Map<String, Object> reescreverConsulta(ConversaState state) {
        String contexto = state.historico().stream()
                .map(turno -> "Usuário: " + turno.pergunta() + "\nAssistente: " + turno.resposta())
                .collect(Collectors.joining("\n\n"));

        String prompt = PROMPT_REESCRITA.formatted(contexto, state.pergunta());
        String perguntaEfetiva = chatModel.chat(prompt).trim();

        log.info("Reescrita: \"{}\" -> \"{}\"", state.pergunta(), perguntaEfetiva);
        return Map.of(ConversaState.PERGUNTA_EFETIVA, perguntaEfetiva);
    }

    /** Nós 3+4+5 — recuperação, avaliação de limiar e resposta/admissão de ausência (via RespostaService). */
    Map<String, Object> responder(ConversaState state) {
        String empresa = state.empresa()
                .orElseThrow(() -> new IllegalStateException("Nó 'responder' alcançado sem empresa fixada no estado."));
        String pergunta = state.perguntaEfetiva();

        RespostaService.Resposta resposta = respostaService.responder(empresa, pergunta);

        List<String> fontesFormatadas = resposta.fontes().stream()
                .map(fonte -> fonte.documento() + " — " + (fonte.secao() == null ? "(sem seção)" : fonte.secao()))
                .toList();

        Turno turno = new Turno(state.pergunta(), resposta.texto());

        Map<String, Object> atualizacao = new HashMap<>();
        atualizacao.put(ConversaState.RESPOSTA, resposta.texto());
        atualizacao.put(ConversaState.FONTES, fontesFormatadas);
        atualizacao.put(ConversaState.HISTORICO, List.of(turno));
        return atualizacao;
    }
}
