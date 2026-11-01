package br.com.andreteixeira.agenterag.conta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O superadmin real contra a aplicação inteira, com as variáveis definidas (valores falsos de
 * teste): criado no boot, entra pelo login, e é atualizado quando a senha ou o usuário mudam. Os
 * casos de variável ausente ou inválida montam um {@link SuperadminReal} à mão, com outras
 * propriedades, sobre o mesmo banco; o caso "linha antiga sem variáveis" está em
 * {@link SuperadminRealSemVariaveisIntegrationTest}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.superadmin.usuario=Chefe.Real", "app.superadmin.senha=senha-real-falsa-1"})
class SuperadminRealIntegrationTest {

    private static final String USUARIO = "chefe.real";
    private static final String SENHA = "senha-real-falsa-1";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(100);

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
    private JdbcClient jdbc;

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();
    private ListAppender<ILoggingEvent> log;
    private Logger logger;

    @BeforeEach
    void recriarOSuperadminRealAPartirDasVariaveis() {
        apagarOQueNaoEhSemeada();
        superadminReal.configurar();
        log = new ListAppender<>();
        log.start();
        logger = (Logger) LoggerFactory.getLogger(SuperadminReal.class);
        logger.addAppender(log);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(log);
        apagarOQueNaoEhSemeada();
    }

    private void apagarOQueNaoEhSemeada() {
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
    }

    private SuperadminReal comAsVariaveis(String usuario, String senha) {
        return new SuperadminReal(new SuperadminProperties(usuario, senha), contas, encoder);
    }

    // ---- Criação e atualização -----------------------------------------------------------

    @Test
    void oBootCriaOSuperadminRealComAsVariaveis() {
        Conta real = contas.buscarSuperadminReal().orElseThrow();

        assertThat(real.usuario()).as("normalizado para minúsculas").isEqualTo(USUARIO);
        assertThat(real.papel()).isEqualTo(Papel.SUPERADMIN);
        assertThat(real.empresa()).isNull();
        assertThat(real.superadminReal()).isTrue();
        assertThat(real.semeada()).isFalse();
        assertThat(real.criadaPor()).isNull();
        assertThat(real.protegida()).isTrue();
        assertThat(real.senhaHash()).startsWith("$2a$").doesNotContain(SENHA);
        assertThat(encoder.matches(SENHA, real.senhaHash())).isTrue();
        assertThat(superadminReal.ativo()).isTrue();
    }

    @Test
    void configurarDeNovoNaoDuplicaNemMexeNoHashSeASenhaNaoMudou() {
        Conta antes = contas.buscarSuperadminReal().orElseThrow();

        superadminReal.configurar();
        superadminReal.configurar();

        Conta depois = contas.buscarSuperadminReal().orElseThrow();
        assertThat(depois.id()).isEqualTo(antes.id());
        assertThat(depois.senhaHash()).as("o mesmo hash, sem rehash à toa").isEqualTo(antes.senhaHash());
        assertThat(contagemDeSuperadminsReais()).isEqualTo(1);
    }

    @Test
    void senhaTrocadaNasVariaveisAtualizaOHashEAAntigaDeixaDeEntrar() throws Exception {
        Conta antes = contas.buscarSuperadminReal().orElseThrow();

        comAsVariaveis(USUARIO, "outra-senha-falsa-2").configurar();

        Conta depois = contas.buscarSuperadminReal().orElseThrow();
        assertThat(depois.id()).isEqualTo(antes.id());
        assertThat(depois.senhaHash()).isNotEqualTo(antes.senhaHash());
        assertThat(login(USUARIO, "outra-senha-falsa-2").statusCode()).isEqualTo(200);
        assertThat(login(USUARIO, SENHA).statusCode()).isEqualTo(401);
    }

    @Test
    void usuarioTrocadoNasVariaveisRenomeiaAMesmaConta() {
        long id = contas.buscarSuperadminReal().orElseThrow().id();

        comAsVariaveis("novo.nome.real", SENHA).configurar();

        Conta depois = contas.buscarSuperadminReal().orElseThrow();
        assertThat(depois.id()).isEqualTo(id);
        assertThat(depois.usuario()).isEqualTo("novo.nome.real");
        assertThat(contas.buscarPorUsuario(USUARIO)).isEmpty();
    }

    // ---- Login e interface ---------------------------------------------------------------

    @Test
    void oSuperadminRealEntraPeloLoginEOSeuNomeNaoApareceNaInterface() throws Exception {
        HttpResponse<String> resposta = login(USUARIO, SENHA);

        assertThat(resposta.statusCode()).isEqualTo(200);
        JsonNode corpo = JSON.readTree(resposta.body());
        assertThat(corpo.get("usuario").asText()).as("mascarado").isEqualTo("superadmin");
        assertThat(corpo.get("papel").asText()).isEqualTo("SUPERADMIN");
        assertThat(corpo.path("empresa").asText(null)).isNull();
        assertThat(resposta.body()).doesNotContain(USUARIO);

        String setCookie = resposta.headers().firstValue("Set-Cookie").orElseThrow();
        HttpResponse<String> eu = get("/api/auth/eu", setCookie.substring(0, setCookie.indexOf(';')));
        assertThat(eu.statusCode()).isEqualTo(200);
        assertThat(eu.body()).contains("\"usuario\":\"superadmin\"").doesNotContain(USUARIO);
    }

    @Test
    void senhaErradaDoSuperadminRealNaoEntra() throws Exception {
        assertThat(login(USUARIO, "senha-errada").statusCode()).isEqualTo(401);
    }

    // ---- Variáveis ausentes ou inválidas -------------------------------------------------

    @Test
    void semAsVariaveisNadaEhCriadoENaoEstaAtivo() {
        apagarOQueNaoEhSemeada();
        SuperadminReal semVariaveis = comAsVariaveis("", "");

        semVariaveis.configurar();

        assertThat(contas.buscarSuperadminReal()).isEmpty();
        assertThat(semVariaveis.ativo()).isFalse();
        assertThat(mensagens(Level.INFO)).containsExactly(
                "Superadmin real não configurado: APP_SUPERADMIN_USUARIO e APP_SUPERADMIN_SENHA ausentes.");
    }

    @Test
    void soUmaDasDuasVariaveisEhErroENadaEhCriado() {
        apagarOQueNaoEhSemeada();

        SuperadminReal soUsuario = comAsVariaveis(USUARIO, "");
        SuperadminReal soSenha = comAsVariaveis("", SENHA);
        soUsuario.configurar();
        soSenha.configurar();

        assertThat(contas.buscarSuperadminReal()).isEmpty();
        assertThat(soUsuario.ativo()).isFalse();
        assertThat(soSenha.ativo()).isFalse();
        assertThat(mensagens(Level.ERROR)).hasSize(2).allSatisfy(m -> assertThat(m).contains("definidas juntas"));
    }

    @Test
    void valoresInvalidosViramErroSemOsValoresENadaEhCriado() {
        apagarOQueNaoEhSemeada();
        String senhaCurta = "curta1";
        String senhaGrandeDemais = "a".repeat(73);

        List<SuperadminReal> invalidos = List.of(
                comAsVariaveis(USUARIO, senhaCurta),
                comAsVariaveis(USUARIO, senhaGrandeDemais),
                comAsVariaveis("ab", SENHA),
                comAsVariaveis("com espaco", SENHA),
                comAsVariaveis("x".repeat(41), SENHA));
        invalidos.forEach(SuperadminReal::configurar);

        assertThat(contas.buscarSuperadminReal()).isEmpty();
        assertThat(invalidos).noneMatch(SuperadminReal::ativo);
        assertThat(mensagens(Level.ERROR)).hasSize(5);
        assertThat(mensagens(Level.ERROR)).allSatisfy(m -> assertThat(m)
                .doesNotContain(senhaCurta, senhaGrandeDemais, "com espaco", SENHA));
    }

    @Test
    void usuarioQueJaEDeOutraContaEErroENaoMexeNaOutraConta() {
        apagarOQueNaoEhSemeada();
        String hashDoAdmin = contas.buscarPorUsuario("admin.bimbam").orElseThrow().senhaHash();
        SuperadminReal colidindo = comAsVariaveis("admin.bimbam", SENHA);

        colidindo.configurar();

        assertThat(colidindo.ativo()).isFalse();
        assertThat(contas.buscarSuperadminReal()).isEmpty();
        assertThat(contas.buscarPorUsuario("admin.bimbam").orElseThrow().senhaHash()).isEqualTo(hashDoAdmin);
        assertThat(mensagens(Level.ERROR)).singleElement().asString().contains("já pertence a outra conta");
    }

    @Test
    void nenhumLogDoSuperadminRealTemOUsuarioOuASenha() {
        apagarOQueNaoEhSemeada();
        superadminReal.configurar();
        comAsVariaveis(USUARIO, "outra-senha-falsa-2").configurar();
        comAsVariaveis("admin.bimbam", SENHA).configurar();
        comAsVariaveis("", "").configurar();

        assertThat(log.list).isNotEmpty().allSatisfy(evento -> {
            assertThat(evento.getFormattedMessage()).doesNotContain(USUARIO, SENHA, "outra-senha-falsa-2", "admin.bimbam");
            assertThat(evento.getThrowableProxy()).isNull();
        });
        assertThat(mensagens(Level.INFO)).contains("Superadmin real configurado a partir das variáveis de ambiente.");
    }

    @Test
    void asPropriedadesNaoMostramNadaNoToString() {
        assertThat(new SuperadminProperties(USUARIO, SENHA).toString()).doesNotContain(USUARIO, SENHA);
    }

    // ---- apoio ---------------------------------------------------------------------------

    private List<String> mensagens(Level nivel) {
        return log.list.stream().filter(e -> e.getLevel() == nivel).map(ILoggingEvent::getFormattedMessage).toList();
    }

    private int contagemDeSuperadminsReais() {
        return jdbc.sql("SELECT COUNT(*) FROM conta WHERE superadmin_real").query(Integer.class).single();
    }

    private HttpResponse<String> login(String usuario, String senha) throws Exception {
        String corpo = JSON.writeValueAsString(Map.of("usuario", usuario, "senha", senha));
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", "203.0.113." + PROXIMO_IP.getAndIncrement())
                .POST(BodyPublishers.ofString(corpo))
                .build();
        return http.send(requisicao, BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String caminho, String cookie) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho))
                .header("Cookie", cookie)
                .GET()
                .build();
        return http.send(requisicao, BodyHandlers.ofString());
    }
}
