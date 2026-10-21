package br.com.andreteixeira.agenterag.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * O outro lado da obtenção do IP: {@code X-Forwarded-For} só vale quando a
 * conexão vem de um proxy interno. Aqui a lista de proxies internos
 * ({@code server.tomcat.remoteip.internal-proxies}) NÃO inclui o loopback de
 * onde o teste conecta — então o cabeçalho, mesmo presente, tem de ser
 * ignorado, e o IP é o da própria conexão. É o que impede um cliente que chegue
 * direto à aplicação de forjar o IP para escapar do limite por IP.
 * <p>
 * Tomcat real e Postgres em container (o contexto precisa dele para subir); o
 * grafo e o limitador são mocks, só para ver o IP que chega até eles. Sem
 * chamada a nenhum provedor. Sem {@code @Tag("llm")}.
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "server.tomcat.remoteip.internal-proxies=192.0.2.1",
            "GEMINI_API_KEY=chave-fake-do-gemini-nao-usada",
            "MARITACA_API_KEY=chave-fake-da-maritaca-nao-usada"
        })
class IpDoClienteSemProxyConfiavelIntegrationTest {

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
    }

    @MockitoBean
    private ConversaService conversaService;

    @MockitoBean
    private LimiteDiarioService limiteDiarioService;

    @LocalServerPort
    private int porta;

    @Test
    void xForwardedForDeUmaConexaoQueNaoEhProxyInternoEhIgnorado() throws Exception {
        when(conversaService.responder(anyString(), any(), anyString()))
                .thenReturn(new ConversaService.RespostaConversa("resposta", List.of(), "bimbam"));

        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/chat"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "203.0.113.77")
                .POST(BodyPublishers.ofString(
                        """
                        {"threadId":"t1","mensagem":"pergunta","empresaEscolhida":"bimbam"}
                        """))
                .build();
        HttpResponse<String> resposta = HttpClient.newHttpClient().send(requisicao, BodyHandlers.ofString());

        assertThat(resposta.statusCode()).isEqualTo(200);
        verify(limiteDiarioService).registrarPergunta("127.0.0.1");
    }
}
