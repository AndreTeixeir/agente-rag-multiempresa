package br.com.andreteixeira.agenterag.ingestao;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Chamada ÚNICA e real à API de embeddings do Gemini — gate da Etapa 3, tarefa
 * 7. NÃO faz parte do {@code mvn test} padrão (excludedGroups=llm no
 * surefire); rodar explicitamente com:
 * <pre>{@code mvn test -Dtest=GeminiEmbeddingSmokeTest -Dgroups=llm}</pre>
 * Exatamente uma chamada nesta classe — não adicionar um segundo teste aqui
 * sem decisão explícita, a cota deste endpoint nunca foi usada nesta conta.
 */
@Tag("llm")
class GeminiEmbeddingSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingSmokeTest.class);

    @Test
    void chamadaUnicaDeTeste() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        assertThat(apiKey).as("GEMINI_API_KEY precisa estar no ambiente").isNotBlank();

        GoogleAiEmbeddingModel model = GoogleAiEmbeddingModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-embedding-001")
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .outputDimensionality(768)
                .logRequestsAndResponses(true)
                .build();

        String textoDeTeste = "Qual o prazo de arrependimento para compras online?";

        Instant inicio = Instant.now();
        Response<Embedding> resposta;
        try {
            resposta = model.embed(textoDeTeste);
        } catch (RuntimeException e) {
            log.error("CHAMADA FALHOU — classe={} mensagem={}", e.getClass().getName(), e.getMessage(), e);
            throw e;
        }
        Duration duracao = Duration.between(inicio, Instant.now());

        float[] vetor = resposta.content().vector();
        double normaL2 = norma(vetor);

        log.info("=== RESULTADO DA CHAMADA ÚNICA ===");
        log.info("dimensao={}", vetor.length);
        log.info("normaL2={}", normaL2);
        log.info("tempoDeResposta={} ms", duracao.toMillis());
        log.info("tokenUsage={}", resposta.tokenUsage());
        log.info("finishReason={}", resposta.finishReason());

        assertThat(vetor.length).isEqualTo(768);
    }

    private static double norma(float[] vetor) {
        double somaQuadrados = 0.0;
        for (float v : vetor) {
            somaQuadrados += (double) v * v;
        }
        return Math.sqrt(somaQuadrados);
    }
}
