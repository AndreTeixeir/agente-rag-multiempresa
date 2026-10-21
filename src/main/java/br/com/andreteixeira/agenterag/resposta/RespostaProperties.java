package br.com.andreteixeira.agenterag.resposta;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de geração de resposta — {@code app.resposta.*} em
 * {@code application.yml}. Nunca hardcoded no código, mesma convenção de
 * {@link br.com.andreteixeira.agenterag.ingestao.IngestionProperties}.
 */
@ConfigurationProperties(prefix = "app.resposta")
public record RespostaProperties(Chat chat, int limit, double threshold) {

    /**
     * Chat model servido por uma API compatível com a da OpenAI. {@code timeout}
     * vale para cada tentativa (conexão e leitura) e {@code maxRetries} é o
     * número de novas tentativas automáticas depois da primeira — a chamada
     * executa no máximo {@code maxRetries + 1} vezes.
     */
    public record Chat(String baseUrl, String apiKey, String modelName, Duration timeout, int maxRetries) {
    }
}
