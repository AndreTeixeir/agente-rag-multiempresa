package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.ingestao.ChunkMetadata;
import br.com.andreteixeira.agenterag.ingestao.EmbeddingProvider;
import br.com.andreteixeira.agenterag.ingestao.VectorStore;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Dublês das duas portas de ingestão para os testes de integração que sobem o
 * grafo de verdade: o embedding e o vector store não saem do processo — zero
 * chamadas ao Gemini. O vector store devolve sempre o mesmo trecho, acima de
 * qualquer limiar, para a resposta sempre chegar ao chat model (que nesses
 * testes é o {@code OpenAiChatModel} real apontado para um
 * {@code ServidorOpenAiFalso}). Usar com {@code @Import}. Público porque é
 * importado por testes de mais de um pacote.
 */
@TestConfiguration
public class DublesDoGrafoConfig {

    @Bean
    @Primary
    EmbeddingProvider embeddingProviderDeTeste() {
        return new EmbeddingProviderDeTeste();
    }

    @Bean
    @Primary
    VectorStore vectorStoreDeTeste() {
        return new VectorStoreComUmChunk();
    }

    /** Nada aqui é invocado de verdade além da consulta: o vector store dublê ignora o vetor. */
    private static final class EmbeddingProviderDeTeste implements EmbeddingProvider {

        private static final int DIMENSAO = 768;

        @Override
        public List<float[]> embedDocuments(List<String> textos) {
            throw new UnsupportedOperationException("o teste não ingere documentos");
        }

        @Override
        public float[] embedQuery(String texto) {
            return new float[DIMENSAO];
        }

        @Override
        public List<float[]> embedQueries(List<String> textos) {
            throw new UnsupportedOperationException("o teste não usa o lote de consultas");
        }

        @Override
        public int dimension() {
            return DIMENSAO;
        }
    }

    private static final class VectorStoreComUmChunk implements VectorStore {

        @Override
        public void add(float[] embedding, String text, ChunkMetadata metadata) {
            throw new UnsupportedOperationException("o teste não ingere documentos");
        }

        @Override
        public List<SearchResult> search(String empresa, float[] queryEmbedding, int limit, double threshold) {
            return List.of(new SearchResult("Trecho de teste.", "documento-teste.md", "Seção Teste", 0.95));
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public long countByEmpresa(String empresa) {
            return 1;
        }

        @Override
        public long countByDocumento(String documento) {
            return 1;
        }

        @Override
        public void deleteByDocumento(String documento) {
            throw new UnsupportedOperationException("o teste não ingere documentos");
        }
    }
}
