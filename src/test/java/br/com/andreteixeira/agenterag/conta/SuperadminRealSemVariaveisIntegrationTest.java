package br.com.andreteixeira.agenterag.conta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A aplicação sem as variáveis do superadmin real (o caso comum, o do notebook sem configuração):
 * ele não existe, e uma linha que um boot anterior tenha deixado no banco não consegue entrar — nem
 * pelo login, nem com um token que ainda estaria dentro da validade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SuperadminRealSemVariaveisIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        PostgresDeTeste.registrar(registry);
    }

    @Autowired
    private SuperadminReal superadminReal;

    @Autowired
    private ContaRepository contas;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JdbcClient jdbc;

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
    }

    @Test
    void semAsVariaveisONaoHaSuperadminRealNemEstaAtivo() {
        assertThat(superadminReal.ativo()).isFalse();
        assertThat(contas.buscarSuperadminReal()).isEmpty();
    }

    @Test
    void umaLinhaDeBootAnteriorNaoEntraPeloLoginNemComTokenAindaValido() throws Exception {
        long id = contas.inserir("real.antigo", encoder.encode("senha-antiga-1"), Papel.SUPERADMIN, null, false, true, null);

        HttpResponse<String> login = postarLogin("real.antigo", "senha-antiga-1");
        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(login.body()).get("erro").asText()).isEqualTo("Usuário ou senha inválidos.");

        HttpResponse<String> eu = getEu(tokens.emitir(id));
        assertThat(eu.statusCode()).as("o token é válido, mas a conta não está configurada neste boot").isEqualTo(401);
    }

    @Test
    void umSuperadminDeDemonstracaoContinuaEntrandoNormalmente() throws Exception {
        assertThat(postarLogin("superadmin.demo", "sabia-demo").statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> postarLogin(String usuario, String senha) throws Exception {
        String corpo = JSON.writeValueAsString(Map.of("usuario", usuario, "senha", senha));
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "203.0.113.230")
                .POST(BodyPublishers.ofString(corpo))
                .build();
        return http.send(requisicao, BodyHandlers.ofString());
    }

    private HttpResponse<String> getEu(String token) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/auth/eu"))
                .header("Cookie", "__Host-sabia-sessao=" + token)
                .GET()
                .build();
        return http.send(requisicao, BodyHandlers.ofString());
    }
}
