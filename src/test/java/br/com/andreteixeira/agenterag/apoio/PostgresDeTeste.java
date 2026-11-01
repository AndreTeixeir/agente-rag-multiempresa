package br.com.andreteixeira.agenterag.apoio;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Um Postgres com pgvector para os testes de integração das contas, compartilhado por todas as
 * classes que o usam (sobe uma vez por execução da JVM e é derrubado pelo Testcontainers ao
 * final), em vez de um contêiner por classe. Porta dinâmica; chaves de API falsas, que só
 * precisam ser texto não vazio para os beans construírem — nenhuma chamada paga.
 * <p>
 * Como o banco é compartilhado, cada teste que grava deixa o estado como achou: as contas
 * semeadas ficam, e o que o teste criar ele apaga.
 */
public final class PostgresDeTeste {

    public static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agenterag_test")
            .withUsername("rag")
            .withPassword("rag");

    static {
        POSTGRES.start();
    }

    private PostgresDeTeste() {
    }

    /** Para chamar de um {@code @DynamicPropertySource}. */
    public static void registrar(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("GEMINI_API_KEY", () -> "chave-fake-do-gemini-nao-usada");
        registry.add("MARITACA_API_KEY", () -> "chave-fake-da-maritaca-nao-usada");
    }
}
