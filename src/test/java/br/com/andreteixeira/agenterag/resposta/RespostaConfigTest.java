package br.com.andreteixeira.agenterag.resposta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Prova offline que {@link RespostaConfig} monta um {@link OpenAiChatModel}
 * com os valores de {@code app.resposta.chat} — sem chave real, sem rede
 * externa, sem {@code @Tag("llm")}. O {@code application.yml} de verdade é
 * carregado; para os testes que fazem chamada, só a {@code base-url} é
 * trocada pelo endereço do {@link ServidorOpenAiFalso} local, e o tempo limite
 * é encurtado para o teste não demorar.
 * <p>
 * O contexto contém só {@link RespostaConfig}: nada de banco, nada de Gemini.
 */
class RespostaConfigTest {

    private static final String CHAVE_FALSA = "chave-fake-da-maritaca";

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RespostaConfig.class)
            .withPropertyValues("MARITACA_API_KEY=" + CHAVE_FALSA);

    @Test
    void montaOpenAiChatModelComOsValoresDoApplicationYml() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();

            RespostaProperties.Chat chat = ctx.getBean(RespostaProperties.class).chat();
            assertThat(chat.baseUrl()).isEqualTo("https://chat.maritaca.ai/api");
            assertThat(chat.apiKey()).isEqualTo(CHAVE_FALSA);
            assertThat(chat.modelName()).isEqualTo("sabiazinho-4");
            assertThat(chat.timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(chat.maxRetries()).isEqualTo(1);

            assertThat(ctx.getBean(ChatModel.class)).isInstanceOf(OpenAiChatModel.class);
        });
    }

    @Test
    void chamaOCaminhoComOModeloEAChaveConfigurados() throws Exception {
        try (ServidorOpenAiFalso servidor = new ServidorOpenAiFalso()) {
            servidor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("olá do servidor falso"));

            contexto.withPropertyValues("app.resposta.chat.base-url=" + servidor.baseUrl())
                    .run(ctx -> {
                        String texto = ctx.getBean(ChatModel.class).chat("pergunta de teste");

                        assertThat(texto).isEqualTo("olá do servidor falso");
                        assertThat(servidor.requisicoes()).isEqualTo(1);
                        assertThat(servidor.caminhos()).containsExactly("/api/chat/completions");
                        assertThat(servidor.autorizacoes()).containsExactly("Bearer " + CHAVE_FALSA);
                        JsonNode corpo = JSON.readTree(servidor.corpos().get(0));
                        assertThat(corpo.get("model").asText()).isEqualTo("sabiazinho-4");
                    });
        }
    }

    @Test
    void tempoLimiteConfiguradoEstouraComTimeoutException() throws Exception {
        try (ServidorOpenAiFalso servidor = new ServidorOpenAiFalso()) {
            servidor.travarPor(Duration.ofSeconds(5));

            contexto.withPropertyValues(
                            "app.resposta.chat.base-url=" + servidor.baseUrl(),
                            "app.resposta.chat.timeout=300ms",
                            "app.resposta.chat.max-retries=0")
                    .run(ctx -> {
                        ChatModel chatModel = ctx.getBean(ChatModel.class);
                        long inicio = System.nanoTime();

                        assertThatThrownBy(() -> chatModel.chat("pergunta de teste")).isInstanceOf(TimeoutException.class);

                        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);
                        assertThat(servidor.requisicoes()).isEqualTo(1);
                        assertThat(gasto).as("estourou no tempo limite, não esperou os 5 s do servidor")
                                .isBetween(Duration.ofMillis(250), Duration.ofSeconds(3));
                    });
        }
    }

    /**
     * {@code max-retries: 1} é uma nova tentativa depois da primeira — duas
     * requisições no total, com a espera de ~0,5 s da política de repetição da
     * LangChain4j entre elas (confirmado lendo {@code RetryUtils} 1.18.1).
     */
    @Test
    void maxRetriesUmFazDuasTentativasQuandoOTempoLimiteEstoura() throws Exception {
        try (ServidorOpenAiFalso servidor = new ServidorOpenAiFalso()) {
            servidor.travarPor(Duration.ofSeconds(5));

            contexto.withPropertyValues(
                            "app.resposta.chat.base-url=" + servidor.baseUrl(),
                            "app.resposta.chat.timeout=300ms",
                            "app.resposta.chat.max-retries=1")
                    .run(ctx -> {
                        ChatModel chatModel = ctx.getBean(ChatModel.class);
                        long inicio = System.nanoTime();

                        assertThatThrownBy(() -> chatModel.chat("pergunta de teste")).isInstanceOf(TimeoutException.class);

                        Duration gasto = Duration.ofNanos(System.nanoTime() - inicio);
                        assertThat(servidor.requisicoes()).isEqualTo(2);
                        assertThat(gasto).as("2 tentativas de 300 ms + espera de ~0,5 s entre elas")
                                .isBetween(Duration.ofMillis(1000), Duration.ofSeconds(4));
                    });
        }
    }

    @Test
    void erroDeLimiteDeRequisicoesEhRepetidoUmaVezEDepoisPropagado() throws Exception {
        try (ServidorOpenAiFalso servidor = new ServidorOpenAiFalso()) {
            servidor.responderSempre(429, ServidorOpenAiFalso.erroDoProvedor("limite de teste"));

            contexto.withPropertyValues("app.resposta.chat.base-url=" + servidor.baseUrl())
                    .run(ctx -> {
                        assertThatThrownBy(() -> ctx.getBean(ChatModel.class).chat("pergunta de teste"))
                                .isInstanceOf(RateLimitException.class);

                        assertThat(servidor.requisicoes()).as("1 tentativa + 1 nova tentativa").isEqualTo(2);
                    });
        }
    }

    @Test
    void erroDeChaveInvalidaNaoEhRepetido() throws Exception {
        try (ServidorOpenAiFalso servidor = new ServidorOpenAiFalso()) {
            servidor.responderSempre(401, ServidorOpenAiFalso.erroDoProvedor("chave de teste inválida"));

            contexto.withPropertyValues("app.resposta.chat.base-url=" + servidor.baseUrl())
                    .run(ctx -> {
                        assertThatThrownBy(() -> ctx.getBean(ChatModel.class).chat("pergunta de teste"))
                                .isInstanceOf(AuthenticationException.class);

                        assertThat(servidor.requisicoes()).as("erro não repetível: só a primeira tentativa").isEqualTo(1);
                    });
        }
    }
}
