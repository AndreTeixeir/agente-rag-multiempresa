package br.com.andreteixeira.agenterag.ingestao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de ingestão — {@code app.ingestao.*} em {@code application.yml}.
 * Nunca hardcoded no código, por regra do projeto.
 */
@ConfigurationProperties(prefix = "app.ingestao")
public record IngestionProperties(
        Embedding embedding, Chunking chunking, VectorStoreConfig vectorStore, long pausaEntreChamadasMs) {

    /**
     * {@code apiKey} vem da variável de ambiente {@code GEMINI_API_KEY} (ver {@code application.yml}),
     * sem valor padrão. Sem a variável o Spring não falha: deixa o texto do placeholder
     * ({@code ${GEMINI_API_KEY}}) como valor e a aplicação subiria, falhando só na primeira
     * chamada ao Google. Por isso a chave é validada aqui, na subida: nula, em branco ou
     * placeholder não resolvido derrubam a aplicação, com uma mensagem que cita só o nome da
     * variável — o valor nunca entra nela.
     */
    public record Embedding(String apiKey, String modelName, int outputDimensionality) {

        public Embedding {
            if (apiKey == null || apiKey.isBlank() || apiKey.strip().startsWith("${")) {
                throw new IllegalStateException(
                        "A variável de ambiente GEMINI_API_KEY (chave dos embeddings, app.ingestao.embedding.api-key) "
                                + "não está definida ou está em branco. A aplicação não sobe sem ela: "
                                + "defina-a no ambiente ou no arquivo .env.");
            }
        }
    }

    /**
     * {@code strategy}: {@code hybrid} (padrão — decisão confirmada, não
     * reabrir) ou {@code recursive} (linha de base para comparação, Etapa
     * 4.3 do plano — nunca padrão).
     */
    public record Chunking(int maxSegmentSizeChars, int maxOverlapSizeChars, String strategy) {
    }

    public record VectorStoreConfig(String table) {
    }
}
