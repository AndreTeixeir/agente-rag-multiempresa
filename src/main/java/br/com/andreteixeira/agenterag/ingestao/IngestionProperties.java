package br.com.andreteixeira.agenterag.ingestao;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades de ingestão — {@code app.ingestao.*} em {@code application.yml}.
 * Nunca hardcoded no código, conforme CLAUDE.md.
 */
@ConfigurationProperties(prefix = "app.ingestao")
public record IngestionProperties(
        Embedding embedding, Chunking chunking, VectorStoreConfig vectorStore, long pausaEntreChamadasMs) {

    public record Embedding(String apiKey, String modelName, int outputDimensionality) {
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
