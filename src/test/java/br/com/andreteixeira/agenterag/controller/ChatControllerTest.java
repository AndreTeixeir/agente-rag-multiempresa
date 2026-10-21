package br.com.andreteixeira.agenterag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.grafo.EstadoGrafoInvalidoException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.LangChain4jException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.bsc.langgraph4j.GraphRunnerException;
import org.bsc.langgraph4j.RunnableConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cobre só o mapeamento exceção -> HTTP de {@link ChatController} +
 * {@link GlobalExceptionHandler} (contrato decidido no relatório da Etapa 6)
 * — {@link ConversaService} mockado, sem grafo, sem Postgres, sem LLM. Sem
 * {@code @Tag("llm")}: roda no {@code mvn test} padrão.
 */
@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversaService conversaService;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void attachLogAppender() {
        logAppender = new ListAppender<>();
        logAppender.start();
        ((Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class)).addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        ((Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class)).detachAppender(logAppender);
    }

    @Test
    void requisicaoValidaRetorna200() throws Exception {
        when(conversaService.responder("t1", "mercado-central", "pergunta"))
                .thenReturn(new ConversaService.RespostaConversa("resposta", List.of("doc — secao"), "mercado-central"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"mercado-central"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("resposta"))
                .andExpect(jsonPath("$.empresa").value("mercado-central"))
                .andExpect(jsonPath("$.fontes[0]").value("doc — secao"));
    }

    @Test
    void slugInvalidoRetorna400() throws Exception {
        when(conversaService.responder(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException(
                        "Empresa 'xyz' não reconhecida. Valores aceitos: [bimbam, mercado-central, santo-pegasus]"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"xyz"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("Empresa 'xyz' não reconhecida. Valores aceitos: [bimbam, mercado-central, santo-pegasus]"));
    }

    @Test
    void empresaAusenteRetorna400() throws Exception {
        when(conversaService.responder(anyString(), any(), anyString()))
                .thenThrow(new IllegalStateException(
                        "Primeira mensagem da conversa precisa informar a empresa — o sistema não adivinha empresa."));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("Primeira mensagem da conversa precisa informar a empresa — o sistema não adivinha empresa."));
    }

    /**
     * Reproduz o achado real da Etapa 6 (verificado pelo navegador/curl contra a
     * aplicação de verdade, não só com o serviço mockado): {@code CompiledGraph}
     * roda os nós via {@code CompletableFuture} e embrulha qualquer exceção
     * lançada dentro de um nó em {@code CompletionException -> ... -> causa real}.
     * A primeira versão do handler só pegava {@code IllegalArgumentException}
     * "nua" e por isso devolvia 500 aqui — {@link #slugInvalidoRetorna400()}
     * sozinho não detectava o problema porque o mock lançava a exceção direto.
     */
    @Test
    void slugInvalidoEmbrulhadoEmCompletionExceptionAindaRetorna400() throws Exception {
        IllegalArgumentException causaReal = new IllegalArgumentException(
                "Empresa 'xyz' não reconhecida. Valores aceitos: [bimbam, mercado-central, santo-pegasus]");
        Exception graphRunnerExceptionSimulada = new Exception("simulando GraphRunnerException", new ExecutionException(causaReal));
        when(conversaService.responder(anyString(), anyString(), anyString()))
                .thenThrow(new CompletionException(graphRunnerExceptionSimulada));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"xyz"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("Empresa 'xyz' não reconhecida. Valores aceitos: [bimbam, mercado-central, santo-pegasus]"));
    }

    @Test
    void empresaAusenteEmbrulhadoEmCompletionExceptionAindaRetorna400() throws Exception {
        IllegalStateException causaReal = new IllegalStateException(
                "Primeira mensagem da conversa precisa informar a empresa — o sistema não adivinha empresa.");
        Exception graphRunnerExceptionSimulada = new Exception("simulando GraphRunnerException", new ExecutionException(causaReal));
        when(conversaService.responder(anyString(), any(), anyString()))
                .thenThrow(new CompletionException(graphRunnerExceptionSimulada));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro")
                        .value("Primeira mensagem da conversa precisa informar a empresa — o sistema não adivinha empresa."));
    }

    @Test
    void mensagemEmBrancoRetorna400SemChamarOServico() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"","empresaEscolhida":"bimbam"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(conversaService);
    }

    @Test
    void invarianteInternaDoGrafoRetorna500SemVazarMensagemOriginalNoCorpo() throws Exception {
        String mensagemInterna = "Grafo não produziu estado final para threadId=t1";
        when(conversaService.responder(anyString(), any(), anyString()))
                .thenThrow(new EstadoGrafoInvalidoException(mensagemInterna));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"bimbam"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Erro interno ao processar a conversa."));

        // A mensagem original vai anexada como throwable (log.error(msg, ex)), não como
        // argumento de formatação — por isso a checagem é em getThrowableProxy(), não em
        // getFormattedMessage() (confirmado rodando o teste: getFormattedMessage() não contém
        // a mensagem da exceção).
        assertThat(logAppender.list)
                .as("mensagem original da exceção deve aparecer no log (não apareceu no corpo da resposta, verificado acima)")
                .anyMatch(evento -> evento.getLevel() == Level.ERROR
                        && evento.getThrowableProxy() != null
                        && mensagemInterna.equals(evento.getThrowableProxy().getMessage()));
    }

    /**
     * Reproduz o achado real de produção (Item 5, Missão 2 — confirmado ao vivo
     * no log da VM: {@code NoResourceFoundException} para {@code /robots.txt}
     * caindo no handler genérico como "erro não mapeado", com stacktrace de
     * ~40 linhas). Cobre o caminho novo: 404 silencioso, sem log de erro.
     */
    @Test
    void recursoEstaticoInexistenteRetorna404SemLogarComoErro() throws Exception {
        mockMvc.perform(get("/robots.txt")).andExpect(status().isNotFound());

        assertThat(logAppender.list)
                .as("404 de recurso estático não deve gerar log de erro")
                .noneMatch(evento -> evento.getLevel() == Level.ERROR);
    }

    // ---- Erros do provedor do modelo (Maritaca) ------------------------------------------
    // As exceções e as cadeias são as reais da LangChain4j 1.18.1 e do LangGraph4j
    // 1.9.0-beta2, registradas no ErrosDoProvedorIntegrationTest contra o grafo de verdade:
    // 429 -> RateLimitException -> HttpException; 402 (sem exceção própria) ->
    // InvalidRequestException -> HttpException; tempo limite -> TimeoutException ->
    // HttpTimeoutException; e o grafo embrulha em CompletionException -> GraphRunnerException ->
    // ExecutionException -> o erro do provedor.

    private static final String MENSAGEM_LIMITE_DE_USO =
            "O assistente atingiu o limite de uso no momento. Tente novamente mais tarde.";
    private static final String MENSAGEM_TEMPO_LIMITE = "O assistente demorou demais para responder. Tente novamente.";

    private static CompletionException embrulhadoPeloGrafo(Throwable erroDoProvedor) {
        RunnableConfig config = RunnableConfig.builder().threadId("t1").build();
        return new CompletionException(new GraphRunnerException(config, new ExecutionException(erroDoProvedor)));
    }

    private static Stream<Arguments> errosDeLimiteOuSaldoDoProvedor() {
        RuntimeException limite = new RateLimitException(new HttpException(429, "limite do provedor"));
        RuntimeException saldo = new InvalidRequestException(new HttpException(402, "saldo do provedor"));
        return Stream.of(
                Arguments.of("429 direto", limite),
                Arguments.of("429 embrulhado em CompletionException", embrulhadoPeloGrafo(limite)),
                Arguments.of("402 direto", saldo),
                Arguments.of("402 embrulhado em CompletionException", embrulhadoPeloGrafo(saldo)));
    }

    private static Stream<Arguments> errosDeTempoLimiteDoProvedor() {
        RuntimeException tempoDoCliente = new TimeoutException(new HttpTimeoutException("request timed out"));
        RuntimeException tempoDoProvedor = new TimeoutException(new HttpException(408, "tempo do provedor"));
        return Stream.of(
                Arguments.of("tempo limite do cliente direto", tempoDoCliente),
                Arguments.of("tempo limite do cliente embrulhado em CompletionException", embrulhadoPeloGrafo(tempoDoCliente)),
                Arguments.of("408 do provedor direto", tempoDoProvedor),
                Arguments.of("408 do provedor embrulhado em CompletionException", embrulhadoPeloGrafo(tempoDoProvedor)));
    }

    private static Stream<Arguments> outrosErrosDoProvedor() {
        return Stream.of(
                        erroDoProvedorComDetalhe("401 chave inválida", new AuthenticationException(new HttpException(401, "detalhe-401"))),
                        erroDoProvedorComDetalhe("500 do provedor", new InternalServerException(new HttpException(500, "detalhe-500"))),
                        erroDoProvedorComDetalhe("400 do provedor", new InvalidRequestException(new HttpException(400, "detalhe-400"))),
                        erroDoProvedorComDetalhe("falha qualquer da biblioteca", new LangChain4jException("detalhe-biblioteca")))
                .flatMap(caso -> caso);
    }

    /** Cada erro entra duas vezes: direto e embrulhado pelo grafo. O detalhe é o que não pode vazar no corpo. */
    private static Stream<Arguments> erroDoProvedorComDetalhe(String nome, RuntimeException erro) {
        String detalhe = erro.getMessage();
        return Stream.of(
                Arguments.of(nome + " direto", erro, detalhe),
                Arguments.of(nome + " embrulhado em CompletionException", embrulhadoPeloGrafo(erro), detalhe));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errosDeLimiteOuSaldoDoProvedor")
    void limiteOuSaldoDoProvedorRetorna503ComMensagemClara(String caso, RuntimeException erro) throws Exception {
        when(conversaService.responder(anyString(), any(), anyString())).thenThrow(erro);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"bimbam"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value(MENSAGEM_LIMITE_DE_USO));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errosDeTempoLimiteDoProvedor")
    void tempoLimiteDoProvedorRetorna503ComMensagemClara(String caso, RuntimeException erro) throws Exception {
        when(conversaService.responder(anyString(), any(), anyString())).thenThrow(erro);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"bimbam"}
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.erro").value(MENSAGEM_TEMPO_LIMITE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("outrosErrosDoProvedor")
    void outrosErrosDoProvedorContinuamRetornando500SemVazarODetalhe(String caso, RuntimeException erro, String detalhe)
            throws Exception {
        when(conversaService.responder(anyString(), any(), anyString())).thenThrow(erro);

        String corpo = mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"bimbam"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.erro").value("Erro interno ao processar a conversa."))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(corpo).as("o detalhe do provedor não pode vazar no corpo").doesNotContain(detalhe);
        assertThat(logAppender.list)
                .as("o detalhe do provedor vai para o log, em nível de erro")
                .anyMatch(evento -> evento.getLevel() == Level.ERROR
                        && evento.getThrowableProxy() != null
                        && detalhe.equals(evento.getThrowableProxy().getMessage()));
    }
}
