package br.com.andreteixeira.agenterag.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.ingestao.ChunkMetadata;
import br.com.andreteixeira.agenterag.ingestao.EmbeddingProvider;
import br.com.andreteixeira.agenterag.ingestao.VectorStore;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Erros do provedor do modelo contra o grafo REAL e um Tomcat REAL — o que o
 * {@code ChatControllerTest} (serviço mockado) não prova: o mock lança a
 * exceção direto, e o {@code CompiledGraph} do LangGraph4j embrulha tudo em
 * {@code CompletionException} (ver {@code GlobalExceptionHandler}).
 * <p>
 * Reais: o contexto Spring inteiro, o grafo com o {@code PostgresSaver}
 * (Postgres em container próprio, porta dinâmica), o {@code RespostaService},
 * o {@code OpenAiChatModel} montado por {@code RespostaConfig} (com a
 * política de repetição e o tempo limite da LangChain4j) e o servidor HTTP.
 * Substituídos: o provedor da Maritaca, por um {@link ServidorOpenAiFalso}
 * local; e o embedding e o vector store, por dublês que não saem do
 * processo — zero chamadas ao Gemini e à Maritaca. Sem {@code @Tag("llm")}.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrosDoProvedorIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agenterag_test")
            .withUsername("rag")
            .withPassword("rag");

    private static final ServidorOpenAiFalso servidor = criarServidor();

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ServidorOpenAiFalso criarServidor() {
        try {
            return new ServidorOpenAiFalso();
        } catch (IOException e) {
            throw new IllegalStateException("não subiu o servidor HTTP local do teste", e);
        }
    }

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("GEMINI_API_KEY", () -> "chave-fake-do-gemini-nao-usada");
        registry.add("MARITACA_API_KEY", () -> "chave-fake-da-maritaca-nao-usada");
        registry.add("app.resposta.chat.base-url", servidor::baseUrl);
        // Só encurta o tempo limite (30 s em produção) para o teste não demorar;
        // max-retries continua o do application.yml.
        registry.add("app.resposta.chat.timeout", () -> "300ms");
    }

    @TestConfiguration
    static class DubleConfig {

        @Bean
        @Primary
        EmbeddingProvider embeddingProviderDeTeste() {
            return new EmbeddingProviderDeTeste();
        }

        @Bean
        @Primary
        VectorStore vectorStoreDeTeste() {
            return new VectorStoreComUmChunk();
        }
    }

    @Autowired
    private ConversaService conversaService;

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

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

    @AfterAll
    static void fecharServidor() {
        servidor.close();
    }

    /**
     * Prova, com o grafo de verdade, a premissa em que o handler se apoia: o
     * serviço recebe a exceção do provedor embrulhada em
     * {@code CompletionException}, e a que identifica o caso
     * ({@code RateLimitException}) fica no meio da cadeia, acima do
     * {@code HttpException} que é a causa raiz.
     */
    @Test
    void grafoEmbrulhaOErroDoProvedorEmCompletionException() {
        servidor.responderSempre(429, ServidorOpenAiFalso.erroDoProvedor("limite de teste"));

        assertThatThrownBy(() -> conversaService.responder(novaConversa(), "bimbam", "pergunta de teste"))
                .isInstanceOf(CompletionException.class)
                .satisfies(erro -> assertThat(nomesDaCadeia(erro))
                        .containsSubsequence("CompletionException", "RateLimitException", "HttpException"));
    }

    @Test
    void limiteDeRequisicoesDoProvedorRetorna503ComMensagemClara() throws Exception {
        servidor.responderSempre(429, ServidorOpenAiFalso.erroDoProvedor("limite de teste"));

        HttpResponse<String> resposta = postarChat(novaConversa(), "pergunta de teste");

        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(erroDo(resposta))
                .isEqualTo("O assistente atingiu o limite de uso no momento. Tente novamente mais tarde.");
    }

    @Test
    void saldoOuPagamentoDoProvedorRetorna503ComMensagemClara() throws Exception {
        servidor.responderSempre(402, ServidorOpenAiFalso.erroDoProvedor("saldo de teste esgotado"));

        HttpResponse<String> resposta = postarChat(novaConversa(), "pergunta de teste");

        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(erroDo(resposta))
                .isEqualTo("O assistente atingiu o limite de uso no momento. Tente novamente mais tarde.");
    }

    @Test
    void tempoLimiteDoProvedorRetorna503ComMensagemClara() throws Exception {
        servidor.travarPor(Duration.ofSeconds(5));

        HttpResponse<String> resposta = postarChat(novaConversa(), "pergunta de teste");

        assertThat(resposta.statusCode()).isEqualTo(503);
        assertThat(erroDo(resposta)).isEqualTo("O assistente demorou demais para responder. Tente novamente.");
    }

    @Test
    void chaveInvalidaRetorna500GenericoESoLogaODetalhe() throws Exception {
        String detalheDoProvedor = "chave de teste inválida";
        servidor.responderSempre(401, ServidorOpenAiFalso.erroDoProvedor(detalheDoProvedor));

        HttpResponse<String> resposta = postarChat(novaConversa(), "pergunta de teste");

        assertThat(resposta.statusCode()).isEqualTo(500);
        assertThat(erroDo(resposta)).isEqualTo("Erro interno ao processar a conversa.");
        assertThat(resposta.body()).as("o detalhe do provedor não pode vazar no corpo").doesNotContain(detalheDoProvedor);
        assertThat(logAppender.list)
                .as("o detalhe do provedor vai para o log, em nível de erro")
                .anyMatch(evento -> evento.getLevel() == Level.ERROR
                        && evento.getThrowableProxy() != null
                        && evento.getThrowableProxy().getMessage().contains(detalheDoProvedor));
    }

    @Test
    void erroInternoDoProvedorRetorna500Generico() throws Exception {
        servidor.responderSempre(500, ServidorOpenAiFalso.erroDoProvedor("falha interna do provedor"));

        HttpResponse<String> resposta = postarChat(novaConversa(), "pergunta de teste");

        assertThat(resposta.statusCode()).isEqualTo(500);
        assertThat(erroDo(resposta)).isEqualTo("Erro interno ao processar a conversa.");
        assertThat(resposta.body()).doesNotContain("falha interna do provedor");
    }

    /**
     * A segunda pergunta de uma conversa faz duas chamadas ao modelo: a
     * reescrita (nó 2) e a resposta (nó 3). O erro na reescrita passa por um
     * nó diferente do da primeira pergunta e precisa dar o mesmo 503.
     */
    @Test
    void falhaNaReescritaDaSegundaPerguntaTambemRetorna503() throws Exception {
        String conversa = novaConversa();
        servidor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("resposta de teste"));
        HttpResponse<String> primeira = postarChat(conversa, "primeira pergunta");
        assertThat(primeira.statusCode()).isEqualTo(200);

        servidor.responderSempre(429, ServidorOpenAiFalso.erroDoProvedor("limite de teste"));
        HttpResponse<String> segunda = postarChat(conversa, "segunda pergunta");

        assertThat(segunda.statusCode()).isEqualTo(503);
        assertThat(erroDo(segunda))
                .isEqualTo("O assistente atingiu o limite de uso no momento. Tente novamente mais tarde.");
    }

    private HttpResponse<String> postarChat(String conversa, String mensagem) throws Exception {
        String corpo = JSON.writeValueAsString(
                Map.of("threadId", conversa, "mensagem", mensagem, "empresaEscolhida", "bimbam"));
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(corpo))
                .build();
        return http.send(requisicao, BodyHandlers.ofString());
    }

    private static String erroDo(HttpResponse<String> resposta) throws IOException {
        return JSON.readTree(resposta.body()).get("erro").asText();
    }

    private static String novaConversa() {
        return UUID.randomUUID().toString();
    }

    private static List<String> nomesDaCadeia(Throwable erro) {
        List<String> nomes = new ArrayList<>();
        for (Throwable atual = erro; atual != null; atual = atual.getCause()) {
            nomes.add(atual.getClass().getSimpleName());
        }
        return nomes;
    }

    /** Nada aqui é invocado de verdade: o vector store dublê devolve sempre o mesmo trecho. */
    private static final class EmbeddingProviderDeTeste implements EmbeddingProvider {

        private static final int DIMENSAO = 768;

        @Override
        public List<float[]> embedDocuments(List<String> textos) {
            throw new UnsupportedOperationException("o teste não ingere documentos");
        }

        @Override
        public float[] embedQuery(String texto) {
            return new float[DIMENSAO];
        }

        @Override
        public List<float[]> embedQueries(List<String> textos) {
            throw new UnsupportedOperationException("o teste não usa o lote de consultas");
        }

        @Override
        public int dimension() {
            return DIMENSAO;
        }
    }

    /** Devolve um trecho fixo acima de qualquer limiar, para a resposta sempre chegar ao chat model. */
    private static final class VectorStoreComUmChunk implements VectorStore {

        @Override
        public void add(float[] embedding, String text, ChunkMetadata metadata) {
            throw new UnsupportedOperationException("o teste não ingere documentos");
        }

        @Override
        public List<SearchResult> search(String empresa, float[] queryEmbedding, int limit, double threshold) {
            return List.of(new SearchResult("Trecho de teste.", "documento-teste.md", "Seção Teste", 0.95));
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public long countByEmpresa(String empresa) {
            return 1;
        }

        @Override
        public long countByDocumento(String documento) {
            return 1;
        }

        @Override
        public void deleteByDocumento(String documento) {
            throw new UnsupportedOperationException("o teste não ingere documentos");
        }
    }
}
