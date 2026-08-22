package br.com.andreteixeira.agenterag.ingestao;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integração ponta a ponta da Etapa 9 (Testcontainers com Postgres real) —
 * lacuna explícita do plano, nunca coberta até esta missão.
 * <p>
 * Container próprio do teste, porta dinâmica — nunca o Postgres do
 * {@code docker-compose} (porta 5434). Imagem {@code pgvector/pgvector:pg16},
 * a mesma do compose — uma {@code postgres:16} limpa não tem a extensão
 * {@code vector} e o Flyway falharia na {@code V1}.
 * <p>
 * {@link EmbeddingProvider} substituído por um dublê determinístico (768
 * dimensões, normalizado em L2) via {@link TestConfiguration} — zero chamadas
 * à API do Gemini. Sem {@code @Tag("llm")}: roda no {@code mvn test} padrão,
 * é justamente o ponto.
 * <p>
 * Corpus reduzido, construído em memória (2 chunks) — nunca invoca
 * {@link IngestionService#ingest} diretamente, porque esse método lê sempre
 * {@code classpath:documentos/md/*.md}, os 14 documentos reais (presentes no
 * classpath de teste porque {@code src/main/resources} é herdado pelo Maven).
 * Em vez disso, exercita {@link EmbeddingProvider} + {@link VectorStore} do
 * mesmo jeito que {@code IngestionService.ingerirDocumento} faz por dentro,
 * com fixture própria.
 */
@Testcontainers
@SpringBootTest
class IngestaoIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agenterag_test")
            .withUsername("rag")
            .withPassword("rag");

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Beans do LangChain4j (GoogleAiEmbeddingModel/GoogleAiGeminiChatModel) só
        // precisam de uma string não vazia para construir — nunca são invocados
        // neste teste, porque o EmbeddingProvider real é substituído abaixo e
        // nada aqui chama o serviço de chat.
        registry.add("GEMINI_API_KEY", () -> "chave-fake-nao-usada-neste-teste");
    }

    @TestConfiguration
    static class EmbeddingProviderDeTesteConfig {

        @Bean
        @Primary
        EmbeddingProvider embeddingProviderDeteministico() {
            return new EmbeddingProviderDeterministico();
        }
    }

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private DataSource dataSource;

    private static final String TEXTO_QUERY = "pergunta de teste sobre mercado central";
    private static final String TEXTO_BIMBAM_ARMADILHA =
            "chunk de teste da bimbam, deliberadamente com o vetor mais próximo da pergunta";
    private static final String TEXTO_MERCADO_CENTRAL_CORRETO =
            "chunk de teste do mercado central, o único que deveria voltar na busca filtrada";

    @Test
    void flywayAplicaAsTresMigracoes() throws SQLException {
        try (var conexao = dataSource.getConnection();
                var statement = conexao.createStatement()) {
            assertThat(existeTabela(statement, "document_chunks")).as("V1__chunks_iniciais.sql").isTrue();
            assertThat(existeTabela(statement, "document_chunks_recursive")).as("V2__chunks_recursivo.sql").isTrue();
            assertThat(existeTabela(statement, "lg4jthread")).as("V3__grafo_checkpoints.sql (LG4JThread)").isTrue();
            assertThat(existeTabela(statement, "lg4jcheckpoint")).as("V3__grafo_checkpoints.sql (LG4JCheckpoint)").isTrue();
        }
    }

    private boolean existeTabela(Statement statement, String nomeTabela) {
        try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + nomeTabela)) {
            resultSet.next();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * O invariante central do projeto (CLAUDE.md): isolamento entre empresas é
     * filtro no banco, não confiança no embedding. A armadilha: o vetor da
     * BimBam é construído deliberadamente MAIS PRÓXIMO da pergunta do que o
     * vetor correto do Mercado Central (comprovado pela primeira busca abaixo,
     * sem filtro cruzado) — e mesmo assim, buscando por "mercado-central", o
     * chunk da BimBam nunca pode aparecer.
     */
    @Test
    void buscaFiltradaPorEmpresaNuncaDevolveChunkDeOutraEmpresaMesmoSendoOMaisProximo() {
        float[] vetorArmadilhaBimbam = embeddingProvider.embedDocuments(List.of(TEXTO_BIMBAM_ARMADILHA)).get(0);
        float[] vetorCorretoMercadoCentral =
                embeddingProvider.embedDocuments(List.of(TEXTO_MERCADO_CENTRAL_CORRETO)).get(0);
        float[] vetorQuery = embeddingProvider.embedQuery(TEXTO_QUERY);

        vectorStore.add(
                vetorArmadilhaBimbam,
                TEXTO_BIMBAM_ARMADILHA,
                new ChunkMetadata("bimbam", "bimbam-fixture.md", "Seção Teste", null));
        vectorStore.add(
                vetorCorretoMercadoCentral,
                TEXTO_MERCADO_CENTRAL_CORRETO,
                new ChunkMetadata("mercado-central", "mercado-central-fixture.md", "Seção Teste", null));

        // Prova que a armadilha funciona: sem filtro cruzado, o vetor da bimbam
        // é o mais próximo da pergunta (score alto).
        List<VectorStore.SearchResult> resultadosBimbam = vectorStore.search("bimbam", vetorQuery, 5, 0.0);
        assertThat(resultadosBimbam).hasSize(1);
        assertThat(resultadosBimbam.get(0).score())
                .as("vetor-armadilha da bimbam precisa ser MUITO próximo da pergunta para o teste valer algo")
                .isGreaterThan(0.99);

        // O teste que vale a etapa inteira: buscando por "mercado-central", o
        // chunk da bimbam (mais próximo em distância pura) NUNCA pode aparecer.
        List<VectorStore.SearchResult> resultados = vectorStore.search("mercado-central", vetorQuery, 5, 0.0);
        assertThat(resultados)
                .as("isolamento por empresa é filtro no banco, não confiança no embedding — "
                        + "mesmo com o vetor da bimbam objetivamente mais próximo, ele não pode vazar aqui")
                .isNotEmpty()
                .allMatch(r -> r.documento().equals("mercado-central-fixture.md"));
        assertThat(resultados.get(0).texto()).isEqualTo(TEXTO_MERCADO_CENTRAL_CORRETO);
        assertThat(resultados.get(0).secao()).isEqualTo("Seção Teste");
        assertThat(resultados.get(0).score())
                .as("score do resultado correto precisa ser MENOR que o da armadilha (0.99+), "
                        + "confirmando que o filtro, não o ranking, é quem garante o isolamento")
                .isLessThan(resultadosBimbam.get(0).score());
    }

    /**
     * Dublê determinístico de {@link EmbeddingProvider} — vetores fixos de 768
     * dimensões, normalizados em L2, construídos a partir de uma direção 2D
     * (as demais 766 posições ficam em zero). A similaridade de cosseno entre
     * dois vetores construídos assim depende só da direção 2D escolhida — o
     * que torna a "armadilha" do teste acima auditável a olho nu.
     */
    private static final class EmbeddingProviderDeterministico implements EmbeddingProvider {

        private static final int DIMENSAO = 768;

        private final Map<String, float[]> vetoresPorTexto = new HashMap<>();

        private EmbeddingProviderDeterministico() {
            // Pergunta: direção (1, 0).
            vetoresPorTexto.put(TEXTO_QUERY, vetorNormalizado(1f, 0f));
            // Armadilha da bimbam: direção quase idêntica à pergunta — cosseno
            // ~0.9999 (deliberadamente o "mais próximo" possível).
            vetoresPorTexto.put(TEXTO_BIMBAM_ARMADILHA, vetorNormalizado(0.999f, 0.045f));
            // Correto do mercado central: direção bem mais afastada — cosseno
            // = 0.6, ainda positivo mas nitidamente mais longe.
            vetoresPorTexto.put(TEXTO_MERCADO_CENTRAL_CORRETO, vetorNormalizado(0.6f, 0.8f));
        }

        private static float[] vetorNormalizado(float x, float y) {
            float norma = (float) Math.sqrt(x * x + y * y);
            float[] vetor = new float[DIMENSAO];
            vetor[0] = x / norma;
            vetor[1] = y / norma;
            return vetor;
        }

        private float[] vetorDe(String texto) {
            float[] vetor = vetoresPorTexto.get(texto);
            if (vetor == null) {
                throw new IllegalArgumentException(
                        "EmbeddingProviderDeterministico não tem vetor fixo para: " + texto);
            }
            return vetor;
        }

        @Override
        public List<float[]> embedDocuments(List<String> textos) {
            return textos.stream().map(this::vetorDe).toList();
        }

        @Override
        public float[] embedQuery(String texto) {
            return vetorDe(texto);
        }

        @Override
        public List<float[]> embedQueries(List<String> textos) {
            return embedDocuments(textos);
        }

        @Override
        public int dimension() {
            return DIMENSAO;
        }
    }
}
