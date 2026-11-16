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
     * <p>
     * {@code apiKey} vem da variável de ambiente {@code MARITACA_API_KEY} (ver
     * {@code application.yml}), sem valor padrão. Sem a variável o Spring deixa o texto do
     * placeholder ({@code ${MARITACA_API_KEY}}) como valor e a aplicação subiria, falhando só na
     * primeira pergunta. Por isso a chave é validada aqui, na subida: nula, em branco ou
     * placeholder não resolvido derrubam a aplicação, com uma mensagem que cita só o nome da
     * variável — o valor nunca entra nela.
     */
    public record Chat(String baseUrl, String apiKey, String modelName, Duration timeout, int maxRetries) {

        public Chat {
            if (apiKey == null || apiKey.isBlank() || apiKey.strip().startsWith("${")) {
                throw new IllegalStateException(
                        "A variável de ambiente MARITACA_API_KEY (chave do chat, app.resposta.chat.api-key) "
                                + "não está definida ou está em branco. A aplicação não sobe sem ela: "
                                + "defina-a no ambiente ou no arquivo .env.");
            }
        }
    }
}
