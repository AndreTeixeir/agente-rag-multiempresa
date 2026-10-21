package br.com.andreteixeira.agenterag.resposta;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Chamada ÚNICA e real ao Sabiazinho-4, pela API da Maritaca, usando o
 * {@link ChatModel} montado por {@link RespostaConfig} a partir do
 * {@code application.yml} — a mesma configuração que a aplicação usa, não um
 * modelo montado à mão. Só {@link RespostaConfig} sobe no contexto: sem banco
 * e sem chave do Gemini. A {@code MARITACA_API_KEY} vem do ambiente ou do
 * {@code .env} (ver {@code spring.config.import} em {@code application.yml}).
 * <p>
 * {@code max-retries} fica em 0 só aqui: com o valor de produção (1), um 429, um
 * 5xx ou um tempo limite faria a LangChain4j repetir sozinha, e seriam duas
 * requisições cobradas em vez de uma.
 * <p>
 * NÃO faz parte do {@code mvn test} padrão (excludedGroups=llm no surefire);
 * rodar explicitamente com:
 * <pre>{@code ./mvnw test -Dtest=MaritacaSmokeTest -Dgroups=llm -Dsurefire.excludedGroups=}</pre>
 * (o {@code -Dsurefire.excludedGroups=} é obrigatório — ver comentário em
 * {@code pom.xml}). Exatamente uma chamada nesta classe — não adicionar um
 * segundo teste aqui sem decisão explícita.
 */
@Tag("llm")
@SpringBootTest(classes = RespostaConfig.class, properties = "app.resposta.chat.max-retries=0")
class MaritacaSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(MaritacaSmokeTest.class);

    @Autowired
    private ChatModel chatModel;

    @Test
    void chamadaUnicaDeTeste() {
        Instant inicio = Instant.now();
        String texto = chatModel.chat("Responda apenas com a palavra: ok");
        Duration duracao = Duration.between(inicio, Instant.now());

        log.info("tempoDeResposta={} ms", duracao.toMillis());
        log.info("tamanhoDaResposta={} caracteres", texto == null ? 0 : texto.length());

        assertThat(texto).isNotBlank();
    }
}
