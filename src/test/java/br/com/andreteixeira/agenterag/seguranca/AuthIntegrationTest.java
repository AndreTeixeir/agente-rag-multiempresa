package br.com.andreteixeira.agenterag.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.apoio.SessaoDeTeste;
import br.com.andreteixeira.agenterag.conta.ContaRepository;
import br.com.andreteixeira.agenterag.conta.Papel;
import br.com.andreteixeira.agenterag.controller.DublesDoGrafoConfig;
import br.com.andreteixeira.agenterag.limite.RelogioMutavel;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Login, sessão e rotas protegidas contra a aplicação inteira: Tomcat real, Postgres real
 * (compartilhado), cadeia de segurança de verdade e o relógio sob controle do teste. O grafo e o
 * modelo são os dublês de {@link DublesDoGrafoConfig} e o {@link ServidorOpenAiFalso} — zero
 * chamadas ao Gemini e à Maritaca.
 * <p>
 * Cada teste usa um IP de documentação ({@code 203.0.113.x}) só dele, pelo {@code X-Forwarded-For}
 * (a conexão do teste é do loopback, que a aplicação trata como proxy), para o limite de
 * tentativas de login de um não atrapalhar o dos outros.
 */
@Import({DublesDoGrafoConfig.class, AuthIntegrationTest.RelogioDeTesteConfig.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthIntegrationTest {

    private static final String SENHA_PUBLICA = "sabia-demo";
    private static final Instant INICIO = Instant.parse("2026-10-21T15:00:00Z"); // 12:00 em São Paulo
    private static final RelogioMutavel RELOGIO = new RelogioMutavel(INICIO);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ServidorOpenAiFalso provedor = criarProvedor();
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);

    private static ServidorOpenAiFalso criarProvedor() {
        try {
            return new ServidorOpenAiFalso();
        } catch (IOException e) {
            throw new IllegalStateException("não subiu o servidor HTTP local do teste", e);
        }
    }

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        PostgresDeTeste.registrar(registry);
        registry.add("app.resposta.chat.base-url", provedor::baseUrl);
        registry.add("app.demo.limites.perguntas-por-dia", () -> "1000");
        registry.add("app.demo.limites.perguntas-por-ip-por-dia", () -> "1000");
    }

    @TestConfiguration
    static class RelogioDeTesteConfig {

        @Bean
        @Primary
        Clock relogioDeTeste() {
            return RELOGIO;
        }
    }

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private ContaRepository contas;

    @Autowired
    private PasswordEncoder encoder;

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    private String ip;

    @BeforeEach
    void umIpSoParaEsteTeste() {
        RELOGIO.definir(INICIO);
        ip = "203.0.113." + PROXIMO_IP.getAndIncrement();
        provedor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("resposta de teste"));
    }

    @AfterAll
    static void fecharProvedor() {
        provedor.close();
    }

    // ---- Login ---------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "superadmin.demo,SUPERADMIN,",
        "admin.bimbam,ADMIN,bimbam",
        "usuario.bimbam,USUARIO,bimbam",
        "admin.mercadocentral,ADMIN,mercado-central",
        "usuario.mercadocentral,USUARIO,mercado-central",
        "admin.santopegasus,ADMIN,santo-pegasus",
        "usuario.santopegasus,USUARIO,santo-pegasus"
    })
    void asSeteContasDeDemonstracaoEntramComASenhaPublica(String usuario, String papel, String empresa) throws Exception {
        HttpResponse<String> resposta = login(usuario, SENHA_PUBLICA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.body());
        assertThat(corpo.get("usuario").asText()).isEqualTo(usuario);
        assertThat(corpo.get("papel").asText()).isEqualTo(papel);
        assertThat(corpo.path("empresa").asText(null)).isEqualTo(empresa);
        assertThat(resposta.body()).as("o token vai só no cookie, nunca no corpo").doesNotContain("eyJ", "token", "hash");
        assertThat(setCookie(resposta))
                .startsWith("__Host-sabia-sessao=")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/")
                .contains("Max-Age=28800");
    }

    @Test
    void senhaErradaEUsuarioInexistenteDaoARespostaIdentica() throws Exception {
        HttpResponse<String> senhaErrada = login("admin.bimbam", "senha-errada");
        HttpResponse<String> semUsuario = login("usuario.que.nao.existe", "senha-errada");

        assertThat(senhaErrada.statusCode()).isEqualTo(401);
        assertThat(semUsuario.statusCode()).isEqualTo(401);
        assertThat(semUsuario.body()).isEqualTo(senhaErrada.body());
        assertThat(JSON.readTree(senhaErrada.body()).get("erro").asText()).isEqualTo("Usuário ou senha inválidos.");
        assertThat(senhaErrada.headers().firstValue("Set-Cookie")).as("login errado não abre sessão").isEmpty();
        assertThat(semUsuario.headers().firstValue("Set-Cookie")).isEmpty();
    }

    @Test
    void oUsuarioDoLoginIgnoraMaiusculasEEspacosNasPontas() throws Exception {
        assertThat(login("  ADMIN.Bimbam ", SENHA_PUBLICA).statusCode()).isEqualTo(200);
    }

    @Test
    void camposEmBrancoOuCorpoQueNaoEhJsonSao400() throws Exception {
        assertThat(postar("/api/auth/login", "{\"usuario\":\"\",\"senha\":\"x\"}", null).statusCode()).isEqualTo(400);
        assertThat(postar("/api/auth/login", "{\"usuario\":\"admin.bimbam\"}", null).statusCode()).isEqualTo(400);
        assertThat(postar("/api/auth/login", "isto não é json", null).statusCode()).isEqualTo(400);
    }

    @Test
    void senhaMuitoLongaNaoQuebraNadaEViraCredencialInvalida() throws Exception {
        HttpResponse<String> resposta = login("admin.bimbam", "a".repeat(200));

        assertThat(resposta.statusCode()).isEqualTo(401);
    }

    // ---- Sessão --------------------------------------------------------------------------

    @Test
    void comACookieDoLoginAContaDaSessaoApareceEmEu() throws Exception {
        String cookie = cookieDe(login("admin.mercadocentral", SENHA_PUBLICA));

        HttpResponse<String> eu = get("/api/auth/eu", cookie);

        assertThat(eu.statusCode()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(eu.body());
        assertThat(corpo.get("usuario").asText()).isEqualTo("admin.mercadocentral");
        assertThat(corpo.get("papel").asText()).isEqualTo("ADMIN");
        assertThat(corpo.get("empresa").asText()).isEqualTo("mercado-central");
    }

    @Test
    void semCookieOuComCookieAdulteradoOuVencidoEhSempre401() throws Exception {
        String cookie = cookieDe(login("usuario.bimbam", SENHA_PUBLICA));

        assertThat(get("/api/auth/eu", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/auth/eu", cookie + "x").statusCode()).as("assinatura adulterada").isEqualTo(401);
        assertThat(get("/api/auth/eu", SessaoDeTeste.NOME_DO_COOKIE + "=lixo").statusCode()).isEqualTo(401);
        assertThat(get("/api/auth/eu", cookie).statusCode()).as("o original ainda vale").isEqualTo(200);

        RELOGIO.definir(INICIO.plus(Duration.ofHours(8)).plusSeconds(1));
        HttpResponse<String> vencido = get("/api/auth/eu", cookie);
        assertThat(vencido.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(vencido.body()).get("erro").asText()).isEqualTo("É preciso entrar para usar este recurso.");
    }

    @Test
    void oLogoutApagaOCookieMesmoSemSessao() throws Exception {
        HttpResponse<String> resposta = postar("/api/auth/logout", "", null);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(setCookie(resposta)).startsWith("__Host-sabia-sessao=;").contains("Max-Age=0").contains("Secure");
    }

    @Test
    void seAContaForExcluidaASessaoDeixaDeValerNaHora() throws Exception {
        long id = contas.inserir("conta.temporaria", encoder.encode("senha-de-teste-1"), Papel.USUARIO, "bimbam", false, false, null);
        try {
            String cookie = cookieDe(login("conta.temporaria", "senha-de-teste-1"));
            assertThat(get("/api/auth/eu", cookie).statusCode()).isEqualTo(200);

            contas.excluir(id);

            assertThat(get("/api/auth/eu", cookie).statusCode()).isEqualTo(401);
        } finally {
            contas.excluir(id);
        }
    }

    // ---- Rotas ---------------------------------------------------------------------------

    @Test
    void oChatExigeLoginEComLoginFunciona() throws Exception {
        String corpo = JSON.writeValueAsString(Map.of(
                "threadId", UUID.randomUUID().toString(), "mensagem", "pergunta de teste", "empresaEscolhida", "bimbam"));

        HttpResponse<String> semLogin = postar("/api/chat", corpo, null);
        assertThat(semLogin.statusCode()).isEqualTo(401);
        assertThat(JSON.readTree(semLogin.body()).get("erro").asText()).isEqualTo("É preciso entrar para usar este recurso.");

        String cookie = cookieDe(login("usuario.bimbam", SENHA_PUBLICA));
        HttpResponse<String> logado = postar("/api/chat", corpo, cookie);
        assertThat(logado.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(logado.body()).get("texto").asText()).isEqualTo("resposta de teste");
    }

    @Test
    void aPaginaOsEstaticosEAsSondasDeSaudeSaoPublicos() throws Exception {
        assertThat(get("/", null).statusCode()).isEqualTo(200);
        assertThat(get("/index.html", null).statusCode()).isEqualTo(200);
        assertThat(get("/favicon.svg", null).statusCode()).isEqualTo(200);
        assertThat(get("/sabia-banner.jpg", null).statusCode()).isEqualTo(200);
        assertThat(get("/health", null).statusCode()).isEqualTo(200);
        assertThat(get("/actuator/health", null).statusCode()).isEqualTo(200);
    }

    @Test
    void todoORestoExigeLogin() throws Exception {
        assertThat(get("/qualquer/coisa", null).statusCode()).isEqualTo(401);
        assertThat(get("/robots.txt", null).statusCode()).isEqualTo(401);
        assertThat(get("/actuator/info", null).statusCode()).isEqualTo(401);
        assertThat(get("/api/contas", null).statusCode()).isEqualTo(401);
        assertThat(postar("/api/admin/ingest", "", null).statusCode()).isEqualTo(401);
    }

    @Test
    void asRotasDeAdministracaoNegamOPapelQueNaoPode() throws Exception {
        String usuario = cookieDe(login("usuario.bimbam", SENHA_PUBLICA));
        String admin = cookieDe(login("admin.bimbam", SENHA_PUBLICA));

        HttpResponse<String> contasDoUsuario = get("/api/contas", usuario);
        assertThat(contasDoUsuario.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(contasDoUsuario.body()).get("erro").asText()).isEqualTo("Você não tem permissão para esta ação.");
        // Só o SUPERADMIN alcança /api/admin (a rota de ingestão só existe no perfil dev, mas a regra vale antes dela).
        assertThat(postar("/api/admin/ingest", "", usuario).statusCode()).isEqualTo(403);
        assertThat(postar("/api/admin/ingest", "", admin).statusCode()).isEqualTo(403);
    }

    @Test
    void naoExisteUsuarioEmMemoriaComSenhaGeradaNoBoot() {
        assertThat(contexto.getBeansOfType(UserDetailsService.class))
                .as("sem isso o Spring Boot imprimiria 'Using generated security password' no log")
                .isEmpty();
    }

    // ---- Tentativas de login -------------------------------------------------------------

    @Test
    void dezErradasPorHoraBloqueiamATentativaSeguinteAteAHoraVirar() throws Exception {
        for (int i = 1; i <= 10; i++) {
            assertThat(login("admin.bimbam", "errada-" + i).statusCode()).as("tentativa " + i).isEqualTo(401);
        }

        HttpResponse<String> bloqueada = login("admin.bimbam", SENHA_PUBLICA);
        assertThat(bloqueada.statusCode()).as("nem a senha certa passa depois das 10 erradas").isEqualTo(429);
        assertThat(JSON.readTree(bloqueada.body()).get("erro").asText())
                .isEqualTo("Muitas tentativas de login erradas. Tente novamente na próxima hora.");
        assertThat(bloqueada.headers().firstValue("Set-Cookie")).isEmpty();

        // 13:00 em São Paulo: a hora virou.
        RELOGIO.definir(INICIO.plus(Duration.ofHours(1)));
        assertThat(login("admin.bimbam", SENHA_PUBLICA).statusCode()).isEqualTo(200);
    }

    @Test
    void oLimiteEPorIpEOutroIpSegueLivre() throws Exception {
        for (int i = 0; i < 10; i++) {
            login("admin.bimbam", "errada");
        }
        assertThat(login("admin.bimbam", SENHA_PUBLICA).statusCode()).isEqualTo(429);

        String outroIp = "198.51.100." + PROXIMO_IP.getAndIncrement();
        assertThat(login("admin.bimbam", SENHA_PUBLICA, outroIp, null).statusCode()).isEqualTo(200);
    }

    @Test
    void oLoginCertoDevolveAVagaEPorIssoSoAsErradasContam() throws Exception {
        for (int i = 0; i < 9; i++) {
            assertThat(login("admin.bimbam", "errada").statusCode()).isEqualTo(401);
        }
        assertThat(login("admin.bimbam", SENHA_PUBLICA).statusCode()).as("o 10º, certo").isEqualTo(200);
        assertThat(login("admin.bimbam", SENHA_PUBLICA).statusCode()).as("o certo devolveu a vaga").isEqualTo(200);

        assertThat(login("admin.bimbam", "errada").statusCode()).as("a 10ª errada ainda cabe").isEqualTo(401);
        assertThat(login("admin.bimbam", SENHA_PUBLICA).statusCode()).as("agora são 10 erradas").isEqualTo(429);
    }

    @Test
    void tentativasEmParaleloNuncaPassamDoLimite() throws Exception {
        int tentativas = 30;
        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < tentativas; i++) {
                Callable<Integer> tarefa = () -> {
                    largada.await();
                    return login("admin.bimbam", "errada").statusCode();
                };
                futuros.add(executor.submit(tarefa));
            }
            largada.countDown();

            int deNaoAutorizado = 0;
            int deMuitasTentativas = 0;
            for (Future<Integer> futuro : futuros) {
                int status = futuro.get(60, TimeUnit.SECONDS);
                if (status == 401) {
                    deNaoAutorizado++;
                } else if (status == 429) {
                    deMuitasTentativas++;
                }
            }

            assertThat(deNaoAutorizado).as("só 10 senhas chegam a ser conferidas").isEqualTo(10);
            assertThat(deMuitasTentativas).isEqualTo(20);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void oIpDasTentativasNaoVaiEmTextoParaOBanco() {
        login("admin.bimbam", "errada");

        List<String> chaves = jdbc.sql("SELECT chave FROM limite_diario WHERE chave LIKE 'login-ip:%'")
                .query(String.class)
                .list();

        assertThat(chaves).isNotEmpty().allSatisfy(chave -> assertThat(chave)
                .matches("login-ip:[0-9a-f]{64}:\\d{2}")
                .hasSizeLessThanOrEqualTo(80)
                .doesNotContain(ip));
    }

    // ---- Barreira de CSRF (Origin) -------------------------------------------------------

    @Test
    void loginComOriginDeOutroSiteEhBarradoAntesDeContarTentativa() throws Exception {
        HttpResponse<String> resposta = login("admin.bimbam", SENHA_PUBLICA, ip, "https://sentiment.andreteixeira.dev.br");

        assertThat(resposta.statusCode()).isEqualTo(403);
        assertThat(JSON.readTree(resposta.body()).get("erro").asText()).isEqualTo("Origem da requisição não permitida.");
        assertThat(resposta.headers().firstValue("Set-Cookie")).isEmpty();
        Integer linhas = jdbc.sql("SELECT COUNT(*) FROM limite_diario WHERE chave LIKE 'login-ip:%' AND chave LIKE :hash")
                .param("hash", "%" + hashDoIp(ip) + "%")
                .query(Integer.class)
                .single();
        assertThat(linhas).as("barrado no filtro: nenhuma tentativa foi contada").isZero();
    }

    @Test
    void loginComOriginDoProprioSitePassaEDeHttpNao() throws Exception {
        // O Host da requisição é 127.0.0.1:<porta>; com Secure (o padrão) o esquema esperado é https.
        assertThat(login("admin.bimbam", SENHA_PUBLICA, ip, "https://127.0.0.1:" + porta).statusCode()).isEqualTo(200);
        assertThat(login("admin.bimbam", SENHA_PUBLICA, ip, "http://127.0.0.1:" + porta).statusCode()).isEqualTo(403);
        assertThat(login("admin.bimbam", SENHA_PUBLICA, ip, "null").statusCode()).isEqualTo(403);
    }

    @Test
    void oOriginTambemVaiNoLogoutENasDemaisRequisicoesQueMudamEstado() throws Exception {
        String corpo = JSON.writeValueAsString(Map.of("threadId", "t1", "mensagem", "oi", "empresaEscolhida", "bimbam"));
        String cookie = cookieDe(login("usuario.bimbam", SENHA_PUBLICA));

        assertThat(enviar("POST", "/api/auth/logout", "", null, ip, "https://outro.example").statusCode()).isEqualTo(403);
        assertThat(enviar("POST", "/api/chat", corpo, cookie, ip, "https://outro.example").statusCode()).isEqualTo(403);
        assertThat(enviar("GET", "/api/auth/eu", null, cookie, ip, "https://outro.example").statusCode())
                .as("GET não muda estado, não é conferido")
                .isEqualTo(200);
    }

    // ---- apoio ---------------------------------------------------------------------------

    private HttpResponse<String> login(String usuario, String senha) {
        return login(usuario, senha, ip, null);
    }

    private HttpResponse<String> login(String usuario, String senha, String ipDoCliente, String origem) {
        try {
            String corpo = JSON.writeValueAsString(Map.of("usuario", usuario, "senha", senha));
            return enviar("POST", "/api/auth/login", corpo, null, ipDoCliente, origem);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> postar(String caminho, String corpo, String cookie) {
        return enviar("POST", caminho, corpo, cookie, ip, null);
    }

    private HttpResponse<String> get(String caminho, String cookie) {
        return enviar("GET", caminho, null, cookie, ip, null);
    }

    private HttpResponse<String> enviar(String metodo, String caminho, String corpo, String cookie, String ipDoCliente, String origem) {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho))
                .method(metodo, corpo == null ? BodyPublishers.noBody() : BodyPublishers.ofString(corpo))
                .header("X-Forwarded-For", ipDoCliente);
        if (corpo != null) {
            requisicao.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            requisicao.header("Cookie", cookie);
        }
        if (origem != null) {
            requisicao.header("Origin", origem);
        }
        try {
            return http.send(requisicao.build(), BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String setCookie(HttpResponse<String> resposta) {
        return resposta.headers().firstValue("Set-Cookie").orElseThrow(() -> new AssertionError("a resposta não tem Set-Cookie"));
    }

    /** O par {@code nome=valor} do Set-Cookie, pronto para voltar no cabeçalho {@code Cookie}. */
    private static String cookieDe(HttpResponse<String> resposta) {
        assertThat(resposta.statusCode()).as("o login para obter o cookie").isEqualTo(200);
        String setCookie = setCookie(resposta);
        return setCookie.substring(0, setCookie.indexOf(';'));
    }

    private static String hashDoIp(String ip) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(ip.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
