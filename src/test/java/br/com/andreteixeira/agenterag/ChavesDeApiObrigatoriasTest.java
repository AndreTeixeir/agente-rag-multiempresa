package br.com.andreteixeira.agenterag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.andreteixeira.agenterag.ingestao.IngestionProperties;
import br.com.andreteixeira.agenterag.resposta.RespostaConfig;
import br.com.andreteixeira.agenterag.resposta.RespostaProperties;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * As chaves de API do embedding ({@code GEMINI_API_KEY}) e do chat ({@code MARITACA_API_KEY})
 * são obrigatórias: sem elas a aplicação não sobe, e a mensagem cita o nome da variável que
 * falta — nunca o valor. Sem esta validação o Spring mantém o texto literal do placeholder e a
 * aplicação subiria, falhando só na primeira chamada ao provedor.
 * <p>
 * Os testes de contexto carregam o {@code application.yml} de verdade, como o
 * {@code RespostaConfigTest}, e trocam só a propriedade da chave. Para simular a variável
 * ausente, a propriedade recebe um placeholder que ninguém define
 * ({@link #PLACEHOLDER_NAO_RESOLVIDO}) — o mesmo texto literal que o Spring deixa quando a
 * variável não existe. O nome real da variável não serve para isso: o teste dependeria do que
 * houvesse no {@code .env} ou no ambiente de quem o roda.
 */
class ChavesDeApiObrigatoriasTest {

    private static final String PLACEHOLDER_NAO_RESOLVIDO = "${VARIAVEL_QUE_NINGUEM_DEFINE_NO_TESTE}";

    private static final String CHAVE_FALSA = "chave-falsa-so-para-o-teste";

    private static final String PROPRIEDADE_DO_EMBEDDING = "app.ingestao.embedding.api-key";

    private static final String PROPRIEDADE_DO_CHAT = "app.resposta.chat.api-key";

    /** Só o binding de {@link IngestionProperties}: o ponto da validação, sem banco nem Gemini. */
    @Configuration
    @EnableConfigurationProperties(IngestionProperties.class)
    static class SoAsPropriedadesDeIngestao {
    }

    private final ApplicationContextRunner contextoDoEmbedding = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(SoAsPropriedadesDeIngestao.class);

    private final ApplicationContextRunner contextoDoChat = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RespostaConfig.class);

    // ------------------------------------------------------------- embedding (GEMINI_API_KEY)

    @Test
    void semAVariavelDoEmbeddingOContextoFalhaCitandoONomeDela() {
        contextoDoEmbedding.withPropertyValues(PROPRIEDADE_DO_EMBEDDING + "=" + PLACEHOLDER_NAO_RESOLVIDO).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GEMINI_API_KEY")
                    .message()
                    .doesNotContain("VARIAVEL_QUE_NINGUEM_DEFINE_NO_TESTE");
        });
    }

    @Test
    void comAVariavelDoEmbeddingEmBrancoOContextoFalhaCitandoONomeDela() {
        contextoDoEmbedding.withPropertyValues(PROPRIEDADE_DO_EMBEDDING + "=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GEMINI_API_KEY");
        });
    }

    @Test
    void comChaveFalsaDoEmbeddingOContextoSobe() {
        contextoDoEmbedding.withPropertyValues(PROPRIEDADE_DO_EMBEDDING + "=" + CHAVE_FALSA).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            IngestionProperties.Embedding embedding = ctx.getBean(IngestionProperties.class).embedding();
            assertThat(embedding.apiKey()).isEqualTo(CHAVE_FALSA);
            assertThat(embedding.modelName()).isEqualTo("gemini-embedding-001");
        });
    }

    // ----------------------------------------------------------------- chat (MARITACA_API_KEY)

    @Test
    void semAVariavelDoChatOContextoFalhaCitandoONomeDela() {
        contextoDoChat.withPropertyValues(PROPRIEDADE_DO_CHAT + "=" + PLACEHOLDER_NAO_RESOLVIDO).run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MARITACA_API_KEY")
                    .message()
                    .doesNotContain("VARIAVEL_QUE_NINGUEM_DEFINE_NO_TESTE");
        });
    }

    @Test
    void comAVariavelDoChatEmBrancoOContextoFalhaCitandoONomeDela() {
        contextoDoChat.withPropertyValues(PROPRIEDADE_DO_CHAT + "=").run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(ctx.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MARITACA_API_KEY");
        });
    }

    @Test
    void comChaveFalsaDoChatOContextoSobe() {
        contextoDoChat.withPropertyValues(PROPRIEDADE_DO_CHAT + "=" + CHAVE_FALSA).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ChatModel.class)).isNotNull();
            assertThat(ctx.getBean(RespostaProperties.class).chat().apiKey()).isEqualTo(CHAVE_FALSA);
        });
    }

    // ------------------------------------------------------- a regra, nos valores de borda

    /** Nula (propriedade sem valor), vazia, só espaços e placeholder não resolvido: nenhuma serve. */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n", "${GEMINI_API_KEY}", "  ${GEMINI_API_KEY}  "})
    void chaveDoEmbeddingInvalidaNaoConstroiORecord(String chave) {
        assertThatThrownBy(() -> new IngestionProperties.Embedding(chave, "gemini-embedding-001", 768))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t\n", "${MARITACA_API_KEY}", "  ${MARITACA_API_KEY}  "})
    void chaveDoChatInvalidaNaoConstroiORecord(String chave) {
        assertThatThrownBy(() -> new RespostaProperties.Chat(
                        "https://exemplo.invalido/api", chave, "modelo", Duration.ofSeconds(30), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MARITACA_API_KEY");
    }

    /** O valor da chave nunca vai para a mensagem, nem parcialmente. */
    @Test
    void aMensagemNuncaTrazOValorRecebido() {
        String valorSuspeito = "${SEGREDO_QUE_NAO_PODE_VAZAR}";

        assertThatThrownBy(() -> new IngestionProperties.Embedding(valorSuspeito, "m", 768))
                .message()
                .doesNotContain("SEGREDO_QUE_NAO_PODE_VAZAR");
        assertThatThrownBy(() -> new RespostaProperties.Chat("https://exemplo.invalido", valorSuspeito, "m", Duration.ofSeconds(1), 0))
                .message()
                .doesNotContain("SEGREDO_QUE_NAO_PODE_VAZAR");
    }

    @Test
    void chavesComValorNormalConstroemOsRecords() {
        assertThat(new IngestionProperties.Embedding(CHAVE_FALSA, "m", 768).apiKey()).isEqualTo(CHAVE_FALSA);
        assertThat(new RespostaProperties.Chat("https://exemplo.invalido", CHAVE_FALSA, "m", Duration.ofSeconds(1), 0).apiKey())
                .isEqualTo(CHAVE_FALSA);
    }
}
