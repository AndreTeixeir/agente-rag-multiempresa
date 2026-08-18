package br.com.andreteixeira.agenterag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.grafo.EstadoGrafoInvalidoException;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
}
