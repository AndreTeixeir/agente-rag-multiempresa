package br.com.andreteixeira.agenterag.resposta;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de geração de resposta — {@code app.resposta.*} em
 * {@code application.yml}. Nunca hardcoded no código, mesma convenção de
 * {@link br.com.andreteixeira.agenterag.ingestao.IngestionProperties}.
 */
@ConfigurationProperties(prefix = "app.resposta")
public record RespostaProperties(Chat chat, int limit, double threshold) {

    public record Chat(String apiKey, String modelName) {
    }
}
