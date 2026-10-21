package br.com.andreteixeira.agenterag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * O contexto inteiro sobe sem Postgres local e sem chaves reais — o que o
 * GitHub Actions exige. Mesmo padrão do {@code IngestaoIntegrationTest}:
 * Postgres em container próprio, porta dinâmica, imagem
 * {@code pgvector/pgvector:pg16} (o Flyway precisa da extensão {@code vector}
 * na V1), e chaves falsas por {@code @DynamicPropertySource}. Nenhuma chamada
 * a API nenhuma: os beans só precisam de uma string não vazia para construir.
 */
@Testcontainers
@SpringBootTest
class AgenteRagApplicationTests {

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
		registry.add("GEMINI_API_KEY", () -> "chave-fake-do-gemini-nao-usada");
		registry.add("MARITACA_API_KEY", () -> "chave-fake-da-maritaca-nao-usada");
	}

	@Test
	void contextLoads() {
	}

}
