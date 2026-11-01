package br.com.andreteixeira.agenterag.controller;

import static br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste.erro;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste;
import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.apoio.SessaoDeTeste;
import br.com.andreteixeira.agenterag.conta.ContaRepository;
import br.com.andreteixeira.agenterag.conta.Papel;
import br.com.andreteixeira.agenterag.conta.SuperadminReal;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A matriz de acesso: cada rota nova e o {@code /api/chat} contra os cinco perfis — sem login, USUARIO,
 * ADMIN, SUPERADMIN de demonstração e SUPERADMIN real —, cada combinação com o status esperado, mais as
 * regras de exclusão (conta protegida, a si mesmo, o superadmin real, outra empresa, administrador).
 * Tomcat e Postgres reais, sessões emitidas pelo {@link TokenService}; zero chamadas pagas.
 * <p>
 * Cada teste é parametrizado pelo perfil; a linha de {@code esperado(...)} lê-se, em ordem: sem login,
 * USUARIO, ADMIN, SUPERADMIN de demonstração, SUPERADMIN real.
 */
@Import(DublesDoGrafoConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.superadmin.usuario=chefe.real", "app.superadmin.senha=senha-real-falsa-1"})
class AcessoMatrizIntegrationTest {

    enum Perfil {
        SEM_LOGIN(null),
        USUARIO("usuario.bimbam"),
        ADMIN("admin.bimbam"),
        SUPERADMIN_DEMO("superadmin.demo"),
        SUPERADMIN_REAL("chefe.real");

        final String usuario;

        Perfil(String usuario) {
            this.usuario = usuario;
        }
    }

    private static final ServidorOpenAiFalso provedor = criarProvedor();
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);
    private static final AtomicInteger PROXIMO_ALVO = new AtomicInteger(1);

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
        registry.add("app.demo.limites.cadastros-por-dia", () -> "1000");
        registry.add("app.demo.limites.cadastros-por-ip-por-dia", () -> "1000");
    }

    @Autowired
    private TokenService tokens;

    @Autowired
    private ContaRepository contas;

    @Autowired
    private SuperadminReal superadminReal;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private JdbcClient jdbc;

    @LocalServerPort
    private int porta;

    private ClienteHttpDeTeste http;
    private String ip;

    @BeforeEach
    void prepararOCenario() {
        apagarOQueNaoEhSemeada();
        superadminReal.configurar();
        http = new ClienteHttpDeTeste(porta);
        ip = "203.0.113." + PROXIMO_IP.getAndIncrement();
        provedor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("resposta de teste"));
    }

    @AfterEach
    void limpar() {
        apagarOQueNaoEhSemeada();
    }

    @AfterAll
    static void fecharProvedor() {
        provedor.close();
    }

    private void apagarOQueNaoEhSemeada() {
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
    }

    private static int esperado(Perfil perfil, int semLogin, int usuario, int admin, int demo, int real) {
        return switch (perfil) {
            case SEM_LOGIN -> semLogin;
            case USUARIO -> usuario;
            case ADMIN -> admin;
            case SUPERADMIN_DEMO -> demo;
            case SUPERADMIN_REAL -> real;
        };
    }

    private String sessao(Perfil perfil) {
        if (perfil.usuario == null) {
            return null;
        }
        return SessaoDeTeste.NOME_DO_COOKIE + "=" + tokens.emitir(idDe(perfil.usuario));
    }

    private long idDe(String usuario) {
        return contas.buscarPorUsuario(usuario).orElseThrow().id();
    }

    /** Uma conta qualquer para servir de alvo da exclusão, criada direto no banco. */
    private long alvo(Papel papel, String empresa, Long criadaPor) {
        return contas.inserir(
                "alvo." + PROXIMO_ALVO.getAndIncrement(), encoder.encode("senha-de-teste-1"), papel, empresa, false, false, criadaPor);
    }

    private int excluir(Perfil perfil, long alvoId) {
        return http.delete("/api/contas/" + alvoId, sessao(perfil), ip).statusCode();
    }

    // ---- Rotas que qualquer logado alcança ------------------------------------------------

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void eu(Perfil perfil) {
        assertThat(http.get("/api/auth/eu", sessao(perfil), ip).statusCode())
                .isEqualTo(esperado(perfil, 401, 200, 200, 200, 200));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void chat(Perfil perfil) {
        Map<String, String> corpo =
                Map.of("threadId", UUID.randomUUID().toString(), "mensagem", "oi", "empresaEscolhida", "bimbam");

        assertThat(http.post("/api/chat", corpo, sessao(perfil), ip).statusCode())
                .isEqualTo(esperado(perfil, 401, 200, 200, 200, 200));
    }

    // ---- Rotas públicas: iguais para todos -----------------------------------------------

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void asRotasPublicasResponderamIgualParaTodosOsPerfis(Perfil perfil) {
        String cookie = sessao(perfil);

        assertThat(http.get("/", cookie, ip).statusCode()).isEqualTo(200);
        assertThat(http.get("/health", cookie, ip).statusCode()).isEqualTo(200);
        assertThat(http.get("/actuator/health", cookie, ip).statusCode()).isEqualTo(200);
        assertThat(http.post("/api/auth/login", Map.of("usuario", "usuario.bimbam", "senha", "sabia-demo"), cookie, ip)
                        .statusCode())
                .isEqualTo(200);
        assertThat(http.post("/api/auth/logout", Map.of(), cookie, ip).statusCode()).isEqualTo(200);
    }

    // ---- Administração de contas ----------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void listar(Perfil perfil) {
        assertThat(http.get("/api/contas", sessao(perfil), ip).statusCode())
                .isEqualTo(esperado(perfil, 401, 403, 200, 200, 200));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void criar(Perfil perfil) {
        Map<String, String> corpo = Map.of(
                "usuario", "nova." + PROXIMO_ALVO.getAndIncrement(),
                "senha", "senha-de-teste-1",
                "papel", "USUARIO",
                "empresa", "bimbam");

        assertThat(http.post("/api/contas", corpo, sessao(perfil), ip).statusCode())
                .isEqualTo(esperado(perfil, 401, 403, 201, 201, 201));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void excluirUsuarioComumDaEmpresaDoAdmin(Perfil perfil) {
        long alvo = alvo(Papel.USUARIO, "bimbam", null);

        assertThat(excluir(perfil, alvo)).isEqualTo(esperado(perfil, 401, 403, 200, 200, 200));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void soOSuperadminExcluiAdministrador(Perfil perfil) {
        long alvo = alvo(Papel.ADMIN, "bimbam", null);

        assertThat(excluir(perfil, alvo)).isEqualTo(esperado(perfil, 401, 403, 403, 200, 200));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void usuarioDeOutraEmpresaNaoEVisivelParaOAdmin(Perfil perfil) {
        long alvo = alvo(Papel.USUARIO, "mercado-central", null);

        assertThat(excluir(perfil, alvo)).isEqualTo(esperado(perfil, 401, 403, 404, 200, 200));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void contaCriadaPeloSuperadminRealSoELeExclui(Perfil perfil) {
        long alvo = alvo(Papel.USUARIO, "bimbam", idDe("chefe.real"));

        assertThat(excluir(perfil, alvo)).isEqualTo(esperado(perfil, 401, 403, 403, 403, 200));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void adminCriadoPeloSuperadminRealTambemFicaProtegidoDoDemo(Perfil perfil) {
        long alvo = alvo(Papel.ADMIN, "bimbam", idDe("chefe.real"));

        assertThat(excluir(perfil, alvo)).isEqualTo(esperado(perfil, 401, 403, 403, 403, 200));
    }

    // Ninguém exclui as contas semeadas — fazem parte da demo e o reset não as recria — nem o real.

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ninguemExcluiUmaContaSemeadaDeUsuario(Perfil perfil) {
        long alvo = idDe("usuario.mercadocentral");

        int status = excluir(perfil, alvo);

        assertThat(status).isEqualTo(esperado(perfil, 401, 403, 404, 403, 403));
        assertThat(contas.buscarPorId(alvo)).as("a conta semeada continua lá").isPresent();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ninguemExcluiUmaContaSemeadaDeAdmin(Perfil perfil) {
        long alvo = idDe("admin.mercadocentral");

        int status = excluir(perfil, alvo);

        assertThat(status).isEqualTo(esperado(perfil, 401, 403, 404, 403, 403));
        assertThat(contas.buscarPorId(alvo)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ninguemExcluiUmaContaSemeadaDaMesmaEmpresaDoAdmin(Perfil perfil) {
        // usuario.bimbam é semeada e da empresa do admin.bimbam: ainda assim, protegida.
        long alvo = idDe("usuario.bimbam");

        int status = excluir(perfil, alvo);

        // O USUARIO tentando excluir a si mesmo é barrado antes, na rota (403).
        assertThat(status).isEqualTo(esperado(perfil, 401, 403, 403, 403, 403));
        assertThat(contas.buscarPorId(alvo)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ninguemExcluiOSuperadminSemeado(Perfil perfil) {
        long alvo = idDe("superadmin.demo");

        int status = excluir(perfil, alvo);

        // O próprio demo se recusa por "a si mesmo" (400); o real, por conta protegida (403).
        assertThat(status).isEqualTo(esperado(perfil, 401, 403, 404, 400, 403));
        assertThat(contas.buscarPorId(alvo)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ninguemExcluiOSuperadminReal(Perfil perfil) {
        long alvo = idDe("chefe.real");

        int status = excluir(perfil, alvo);

        // Quem não é o real nem sabe que ele existe (404); o próprio real recusa a si mesmo (400).
        assertThat(status).isEqualTo(esperado(perfil, 401, 403, 404, 404, 400));
        assertThat(contas.buscarSuperadminReal()).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void ninguemExcluiASiMesmo(Perfil perfil) {
        if (perfil == Perfil.SEM_LOGIN) {
            return;
        }
        long propria = idDe(perfil.usuario);

        int status = excluir(perfil, propria);

        // O USUARIO é barrado na rota (403); os demais, pela regra "a si mesmo" (400).
        assertThat(status).isEqualTo(esperado(perfil, 401, 403, 400, 400, 400));
        assertThat(contas.buscarPorId(propria)).isPresent();
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void idInexistente(Perfil perfil) {
        assertThat(excluir(perfil, 999_999_999L)).isEqualTo(esperado(perfil, 401, 403, 404, 404, 404));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void idQueNaoEhNumero(Perfil perfil) {
        assertThat(http.delete("/api/contas/abc", sessao(perfil), ip).statusCode())
                .isEqualTo(esperado(perfil, 401, 403, 400, 400, 400));
    }

    @ParameterizedTest
    @EnumSource(Perfil.class)
    void rotaDeIngestaoDoPerfilDevSoAlcancaOSuperadmin(Perfil perfil) {
        // Fora do perfil dev a rota nem existe (404), mas a autorização vem antes: só o SUPERADMIN passa dela.
        assertThat(http.get("/api/admin/ingest", sessao(perfil), ip).statusCode())
                .isEqualTo(esperado(perfil, 401, 403, 403, 404, 404));
    }

    // ---- As mensagens da recusa ----------------------------------------------------------

    @Test
    void aMensagemDeContaProtegidaEClara() {
        HttpResponse<String> resposta =
                http.delete("/api/contas/" + idDe("usuario.mercadocentral"), sessao(Perfil.SUPERADMIN_DEMO), ip);

        assertThat(resposta.statusCode()).isEqualTo(403);
        assertThat(erro(resposta)).isEqualTo("Esta conta é protegida e não pode ser excluída.");
    }

    @Test
    void aMensagemDeAdministradorEClara() {
        HttpResponse<String> resposta =
                http.delete("/api/contas/" + alvo(Papel.ADMIN, "bimbam", null), sessao(Perfil.ADMIN), ip);

        assertThat(resposta.statusCode()).isEqualTo(403);
        assertThat(erro(resposta)).isEqualTo("Só o superadmin exclui administradores.");
    }

    @Test
    void aMensagemDeContaInvisivelEDeNaoEncontrada() {
        HttpResponse<String> resposta =
                http.delete("/api/contas/" + alvo(Papel.USUARIO, "mercado-central", null), sessao(Perfil.ADMIN), ip);

        assertThat(resposta.statusCode()).isEqualTo(404);
        assertThat(erro(resposta)).isEqualTo("Conta não encontrada.");
    }

    @ParameterizedTest
    @EnumSource(
            value = Perfil.class,
            names = {"ADMIN", "SUPERADMIN_DEMO", "SUPERADMIN_REAL"})
    void aMensagemDeExcluirASiMesmo(Perfil perfil) {
        HttpResponse<String> resposta = http.delete("/api/contas/" + idDe(perfil.usuario), sessao(perfil), ip);

        assertThat(erro(resposta)).isEqualTo("Você não pode excluir a própria conta.");
    }
}
