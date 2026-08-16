package br.com.andreteixeira.agenterag.ingestao;

import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring das duas portas de ingestão (EmbeddingProvider, VectorStore) e da
 * ChunkingStrategy. As implementações concretas (Gemini, pgvector, híbrida ou
 * recursiva pura) ficam isoladas aqui — trocar qualquer uma delas é mudar
 * este arquivo, não o resto da ingestão.
 */
@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
public class IngestionConfig {

    @Bean
    public GoogleAiEmbeddingModel googleAiEmbeddingModel(IngestionProperties properties) {
        IngestionProperties.Embedding embeddingProperties = properties.embedding();
        return GoogleAiEmbeddingModel.builder()
                .apiKey(embeddingProperties.apiKey())
                .modelName(embeddingProperties.modelName())
                // RETRIEVAL_DOCUMENT é o valor que embedAll() usa (não aceita
                // override por chamada) — ver GeminiEmbeddingProvider para a
                // explicação completa, com evidência de bytecode.
                .taskType(GoogleAiEmbeddingModel.TaskType.RETRIEVAL_DOCUMENT)
                .outputDimensionality(embeddingProperties.outputDimensionality())
                .build();
    }

    @Bean
    public EmbeddingProvider embeddingProvider(GoogleAiEmbeddingModel googleAiEmbeddingModel, IngestionProperties properties) {
        return new GeminiEmbeddingProvider(googleAiEmbeddingModel, properties.embedding().outputDimensionality());
    }

    @Bean
    public ChunkingStrategy chunkingStrategy(IngestionProperties properties) {
        IngestionProperties.Chunking chunkingProperties = properties.chunking();
        int tamanho = chunkingProperties.maxSegmentSizeChars();
        int overlap = chunkingProperties.maxOverlapSizeChars();
        String estrategia = chunkingProperties.strategy();
        if ("recursive".equals(estrategia)) {
            return new RecursiveChunkingStrategy(tamanho, overlap);
        }
        if (estrategia != null && !"hybrid".equals(estrategia)) {
            throw new IllegalStateException(
                    "app.ingestao.chunking.strategy inválido: " + estrategia + " (use 'hybrid' ou 'recursive')");
        }
        return new HybridChunkingStrategy(tamanho, overlap);
    }

    @Bean
    public PgVectorEmbeddingStore pgVectorEmbeddingStore(DataSource dataSource, IngestionProperties properties) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(properties.vectorStore().table())
                .dimension(properties.embedding().outputDimensionality())
                // Schema, colunas de metadado e índices já existem — criados pela
                // migração Flyway (V1__chunks_iniciais.sql), não por esta store.
                .createTable(false)
                .useIndex(false)
                .skipCreateVectorExtension(true)
                .metadataStorageConfig(chunkMetadataStorageConfig())
                .build();
    }

    @Bean
    public VectorStore vectorStore(
            PgVectorEmbeddingStore pgVectorEmbeddingStore, DataSource dataSource, IngestionProperties properties) {
        return new PgVectorStore(pgVectorEmbeddingStore, dataSource, properties.vectorStore().table());
    }

    /**
     * Espelha exatamente as colunas de metadado criadas em
     * {@code V1__chunks_iniciais.sql} — empresa, documento, secao, pagina.
     * COLUMN_PER_KEY (em vez de JSON/JSONB combinado) porque a lista de
     * metadados é fixa e conhecida, e porque "empresa" precisa ser uma coluna
     * de verdade, indexável e filtrável em SQL puro — não um campo dentro de
     * um blob JSON.
     */
    private static MetadataStorageConfig chunkMetadataStorageConfig() {
        return DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(List.of(
                        "empresa varchar not null",
                        "documento varchar not null",
                        "secao varchar null",
                        "pagina integer null"))
                .indexes(List.of("empresa"))
                .build();
    }
}
