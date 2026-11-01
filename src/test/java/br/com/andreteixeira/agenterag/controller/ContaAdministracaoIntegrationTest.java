package br.com.andreteixeira.agenterag.controller;

import static br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste.erro;
import static br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste.ler;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste;
import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.apoio.SessaoDeTeste;
import br.com.andreteixeira.agenterag.conta.Conta;
import br.com.andreteixeira.agenterag.conta.ContaRepository;
import br.com.andreteixeira.agenterag.conta.SuperadminReal;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Criar e listar contas contra a aplicação inteira (Tomcat e Postgres reais, sessões emitidas pelo
 * {@link TokenService}). Os tetos de cadastro ficam altos aqui — os tetos de verdade são o assunto de
 * {@link CadastroLimitesIntegrationTest} — e o superadmin real é configurado pelas variáveis, com
 * valores falsos de teste. A matriz de acesso e as regras de exclusão estão em
 * {@link AcessoMatrizIntegrationTest}. Zero chamadas pagas.
 */
@Import(DublesDoGrafoConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.superadmin.usuario=chefe.real", "app.superadmin.senha=senha-real-falsa-1"})
class ContaAdministracaoIntegrationTest {

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

    private String sessao(String usuario) {
        return SessaoDeTeste.NOME_DO_COOKIE + "=" + tokens.emitir(contas.buscarPorUsuario(usuario).orElseThrow().id());
    }

    private static Map<String, String> nova(String usuario, String papel, String empresa) {
        Map<String, String> corpo = new java.util.HashMap<>();
        corpo.put("usuario", usuario);
        corpo.put("senha", "senha-de-teste-1");
        if (papel != null) {
            corpo.put("papel", papel);
        }
        if (empresa != null) {
            corpo.put("empresa", empresa);
        }
        return corpo;
    }

    private HttpResponse<String> criar(String sessao, Map<String, String> corpo) {
        return http.post("/api/contas", corpo, sessao, ip);
    }

    private List<String> usuariosDaListagem(String sessao) {
        HttpResponse<String> resposta = http.get("/api/contas", sessao, ip);
        assertThat(resposta.statusCode()).isEqualTo(200);
        List<String> usuarios = new ArrayList<>();
        ler(resposta).forEach(conta -> usuarios.add(conta.get("usuario").asText()));
        return usuarios;
    }

    // ---- Criar ---------------------------------------------------------------------------

    @Test
    void adminCriaUmUsuarioDaPropriaEmpresaEEleEntraEConversa() throws Exception {
        HttpResponse<String> resposta = criar(sessao("admin.bimbam"), nova("novo.usuario", null, null));

        assertThat(resposta.statusCode()).isEqualTo(201);
        JsonNode criada = ler(resposta);
        assertThat(criada.get("usuario").asText()).isEqualTo("novo.usuario");
        assertThat(criada.get("papel").asText()).isEqualTo("USUARIO");
        assertThat(criada.get("empresa").asText()).isEqualTo("bimbam");
        assertThat(criada.get("protegida").asBoolean()).isFalse();
        assertThat(criada.get("podeExcluir").asBoolean()).isTrue();
        assertThat(criada.has("senha") || criada.has("senhaHash") || criada.has("senha_hash")).isFalse();
        assertThat(resposta.body()).doesNotContain("senha-de-teste-1", "$2a$");

        Conta gravada = contas.buscarPorUsuario("novo.usuario").orElseThrow();
        assertThat(gravada.criadaPor()).isEqualTo(contas.buscarPorUsuario("admin.bimbam").orElseThrow().id());
        assertThat(gravada.semeada()).isFalse();
        assertThat(gravada.superadminReal()).isFalse();
        assertThat(gravada.senhaHash()).startsWith("$2a$").doesNotContain("senha-de-teste-1");

        // O usuário novo entra com a senha que o admin definiu e conversa só com a empresa dele.
        HttpResponse<String> login = http.post("/api/auth/login", Map.of("usuario", "novo.usuario", "senha", "senha-de-teste-1"), null, ip);
        assertThat(login.statusCode()).isEqualTo(200);
        String cookie = login.headers().firstValue("Set-Cookie").orElseThrow();
        HttpResponse<String> chat = http.post(
                "/api/chat",
                Map.of("threadId", UUID.randomUUID().toString(), "mensagem", "oi", "empresaEscolhida", "mercado-central"),
                cookie.substring(0, cookie.indexOf(';')),
                ip);
        assertThat(chat.statusCode()).isEqualTo(200);
        assertThat(ler(chat).get("empresa").asText()).isEqualTo("bimbam");
    }

    @Test
    void oServidorForcaPapelEEmpresaQuandoQuemCriaEAdmin() {
        HttpResponse<String> resposta =
                criar(sessao("admin.bimbam"), nova("tentando.admin", "ADMIN", "mercado-central"));

        assertThat(resposta.statusCode()).isEqualTo(201);
        assertThat(ler(resposta).get("papel").asText()).as("admin não cria admin").isEqualTo("USUARIO");
        assertThat(ler(resposta).get("empresa").asText()).as("admin não escolhe empresa").isEqualTo("bimbam");
        Conta gravada = contas.buscarPorUsuario("tentando.admin").orElseThrow();
        assertThat(gravada.papel().name()).isEqualTo("USUARIO");
        assertThat(gravada.empresa()).isEqualTo("bimbam");
    }

    @Test
    void cadaAdminCriaNaEmpresaDele() {
        assertThat(ler(criar(sessao("admin.santopegasus"), nova("do.pegasus", null, null))).get("empresa").asText())
                .isEqualTo("santo-pegasus");
        assertThat(ler(criar(sessao("admin.mercadocentral"), nova("do.mercado", null, null))).get("empresa").asText())
                .isEqualTo("mercado-central");
    }

    @Test
    void superadminDemoCriaAdminEUsuarioEmQualquerEmpresaEPodeHaverVariosAdminsPorEmpresa() {
        String demo = sessao("superadmin.demo");

        HttpResponse<String> admin1 = criar(demo, nova("outro.admin.bimbam", "ADMIN", "bimbam"));
        HttpResponse<String> admin2 = criar(demo, nova("mais.um.admin.bimbam", "admin", "bimbam"));
        HttpResponse<String> usuario = criar(demo, nova("novo.no.mercado", "USUARIO", "mercado-central"));

        assertThat(admin1.statusCode()).isEqualTo(201);
        assertThat(ler(admin1).get("papel").asText()).isEqualTo("ADMIN");
        assertThat(admin2.statusCode()).as("papel em minúsculas também vale").isEqualTo(201);
        assertThat(usuario.statusCode()).isEqualTo(201);
        assertThat(ler(usuario).get("empresa").asText()).isEqualTo("mercado-central");
    }

    @Test
    void ninguemCriaSuperadminPelaApi() {
        String demo = sessao("superadmin.demo");
        String real = sessao("chefe.real");

        for (String sessao : List.of(demo, real)) {
            HttpResponse<String> resposta = criar(sessao, nova("quer.ser.super", "SUPERADMIN", null));
            assertThat(resposta.statusCode()).isEqualTo(400);
            assertThat(erro(resposta)).isEqualTo("Informe o papel da nova conta: ADMIN ou USUARIO.");
        }
        assertThat(contas.buscarPorUsuario("quer.ser.super")).isEmpty();
    }

    @Test
    void superadminPrecisaDePapelEEmpresaValidos() {
        String demo = sessao("superadmin.demo");

        assertThat(criar(demo, nova("sem.papel", null, "bimbam")).statusCode()).isEqualTo(400);
        assertThat(criar(demo, nova("papel.ruim", "DONO", "bimbam")).statusCode()).isEqualTo(400);
        HttpResponse<String> semEmpresa = criar(demo, nova("sem.empresa", "USUARIO", null));
        assertThat(semEmpresa.statusCode()).isEqualTo(400);
        assertThat(erro(semEmpresa)).startsWith("Empresa inválida. Valores aceitos:");
        assertThat(criar(demo, nova("empresa.ruim", "USUARIO", "empresa-que-nao-existe")).statusCode()).isEqualTo(400);
        assertThat(contas.buscarPorUsuario("sem.papel")).isEmpty();
        assertThat(contas.buscarPorUsuario("empresa.ruim")).isEmpty();
    }

    @Test
    void usuarioJaEmUsoEhRecusadoInclusiveSeSoMudaACaixa() {
        String admin = sessao("admin.bimbam");
        assertThat(criar(admin, nova("repetido", null, null)).statusCode()).isEqualTo(201);

        HttpResponse<String> igual = criar(admin, nova("repetido", null, null));
        HttpResponse<String> outraCaixa = criar(admin, nova("REPETIDO", null, null));
        HttpResponse<String> deUmaSemeada = criar(admin, nova("admin.bimbam", null, null));

        for (HttpResponse<String> resposta : List.of(igual, outraCaixa, deUmaSemeada)) {
            assertThat(resposta.statusCode()).isEqualTo(400);
            assertThat(erro(resposta)).isEqualTo("Esse nome de usuário já está em uso.");
        }
    }

    @Test
    void oUsuarioEhGravadoEmMinusculas() {
        HttpResponse<String> resposta = criar(sessao("admin.bimbam"), nova("  Maiuscula.Aqui ", null, null));

        assertThat(resposta.statusCode()).isEqualTo(201);
        assertThat(ler(resposta).get("usuario").asText()).isEqualTo("maiuscula.aqui");
        assertThat(contas.buscarPorUsuario("maiuscula.aqui")).isPresent();
    }

    @Test
    void usuarioForaDoFormatoEhRecusado() {
        String admin = sessao("admin.bimbam");

        for (String ruim : List.of("ab", "com espaco", "x".repeat(41), "acentuação", "a/b", "semi;colon")) {
            HttpResponse<String> resposta = criar(admin, nova(ruim, null, null));
            assertThat(resposta.statusCode()).as(ruim).isEqualTo(400);
            assertThat(erro(resposta)).as(ruim).startsWith("Usuário inválido:");
        }
    }

    @Test
    void senhaPrecisaTerDe8CaracteresAte72Bytes() {
        String admin = sessao("admin.bimbam");

        Map<String, String> curta = nova("senha.curta", null, null);
        curta.put("senha", "1234567");
        HttpResponse<String> respostaCurta = criar(admin, curta);
        assertThat(respostaCurta.statusCode()).isEqualTo(400);
        assertThat(erro(respostaCurta)).isEqualTo("A senha precisa ter pelo menos 8 caracteres.");

        Map<String, String> noLimiteInferior = nova("senha.oito", null, null);
        noLimiteInferior.put("senha", "12345678");
        assertThat(criar(admin, noLimiteInferior).statusCode()).isEqualTo(201);

        Map<String, String> grande = nova("senha.grande", null, null);
        grande.put("senha", "a".repeat(73));
        HttpResponse<String> respostaGrande = criar(admin, grande);
        assertThat(respostaGrande.statusCode()).isEqualTo(400);
        assertThat(erro(respostaGrande)).isEqualTo("A senha pode ter no máximo 72 bytes.");

        Map<String, String> noLimiteSuperior = nova("senha.setenta.e.dois", null, null);
        noLimiteSuperior.put("senha", "a".repeat(72));
        assertThat(criar(admin, noLimiteSuperior).statusCode()).isEqualTo(201);

        assertThat(contas.buscarPorUsuario("senha.curta")).isEmpty();
        assertThat(contas.buscarPorUsuario("senha.grande")).isEmpty();
    }

    @Test
    void camposEmBrancoOuCorpoQueNaoEhJsonSao400() {
        String admin = sessao("admin.bimbam");

        assertThat(criar(admin, Map.of("usuario", "", "senha", "senha-de-teste-1")).statusCode()).isEqualTo(400);
        assertThat(criar(admin, Map.of("usuario", "sem.senha")).statusCode()).isEqualTo(400);
        assertThat(http.enviar("POST", "/api/contas", "isto não é json", admin, ip).statusCode()).isEqualTo(400);
    }

    @Test
    void usuarioComumNaoCriaESemLoginTambemNao() {
        assertThat(criar(sessao("usuario.bimbam"), nova("nao.pode", null, null)).statusCode()).isEqualTo(403);
        assertThat(criar(null, nova("nao.pode", null, null)).statusCode()).isEqualTo(401);
        assertThat(contas.buscarPorUsuario("nao.pode")).isEmpty();
    }

    @Test
    void contaCriadaPeloSuperadminRealFicaProtegidaEGuardaQuemCriou() {
        long idDoReal = contas.buscarSuperadminReal().orElseThrow().id();

        HttpResponse<String> resposta = criar(sessao("chefe.real"), nova("criada.pelo.real", "ADMIN", "bimbam"));

        assertThat(resposta.statusCode()).isEqualTo(201);
        assertThat(ler(resposta).get("protegida").asBoolean()).isTrue();
        Conta gravada = contas.buscarPorUsuario("criada.pelo.real").orElseThrow();
        assertThat(gravada.criadaPor()).isEqualTo(idDoReal);
        assertThat(gravada.criadaPelaReal()).isTrue();
    }

    // ---- Listar --------------------------------------------------------------------------

    @Test
    void adminVeSoOsUsuariosComunsDaPropriaEmpresa() {
        criar(sessao("admin.bimbam"), nova("criado.na.bimbam", null, null));
        criar(sessao("admin.mercadocentral"), nova("criado.no.mercado", null, null));
        criar(sessao("superadmin.demo"), nova("outro.admin.na.bimbam", "ADMIN", "bimbam"));

        assertThat(usuariosDaListagem(sessao("admin.bimbam")))
                .containsExactlyInAnyOrder("usuario.bimbam", "criado.na.bimbam");
    }

    @Test
    void adminVeAsFlagsDeProtecaoEExclusao() {
        criar(sessao("admin.bimbam"), nova("criado.na.bimbam", null, null));

        JsonNode lista = ler(http.get("/api/contas", sessao("admin.bimbam"), ip));

        for (JsonNode conta : lista) {
            if (conta.get("usuario").asText().equals("usuario.bimbam")) {
                assertThat(conta.get("protegida").asBoolean()).isTrue();
                assertThat(conta.get("podeExcluir").asBoolean()).isFalse();
            } else {
                assertThat(conta.get("usuario").asText()).isEqualTo("criado.na.bimbam");
                assertThat(conta.get("protegida").asBoolean()).isFalse();
                assertThat(conta.get("podeExcluir").asBoolean()).isTrue();
            }
        }
    }

    @Test
    void superadminDemoVeTodasAsContasMenosOSuperadminReal() {
        criar(sessao("admin.bimbam"), nova("criado.na.bimbam", null, null));

        HttpResponse<String> resposta = http.get("/api/contas", sessao("superadmin.demo"), ip);
        List<String> usuarios = usuariosDaListagem(sessao("superadmin.demo"));

        assertThat(usuarios)
                .contains("superadmin.demo", "admin.bimbam", "usuario.bimbam", "admin.mercadocentral",
                        "usuario.mercadocentral", "admin.santopegasus", "usuario.santopegasus", "criado.na.bimbam")
                .hasSize(8)
                .doesNotContain("chefe.real");
        assertThat(resposta.body()).as("nem o nome nem o papel do real aparecem").doesNotContain("chefe.real");
    }

    @Test
    void oSuperadminRealVeTodasMenosEleMesmoEPodeExcluirTudoMenosAsSemeadas() {
        criar(sessao("admin.bimbam"), nova("criado.pela.demo", null, null));

        JsonNode lista = ler(http.get("/api/contas", sessao("chefe.real"), ip));

        List<String> usuarios = new ArrayList<>();
        lista.forEach(conta -> {
            usuarios.add(conta.get("usuario").asText());
            boolean semeada = !conta.get("usuario").asText().equals("criado.pela.demo");
            assertThat(conta.get("podeExcluir").asBoolean())
                    .as(conta.get("usuario").asText())
                    .isEqualTo(!semeada);
        });
        assertThat(usuarios).hasSize(8).doesNotContain("chefe.real");
    }

    @Test
    void usuarioComumNaoListaESemLoginTambemNao() {
        assertThat(http.get("/api/contas", sessao("usuario.bimbam"), ip).statusCode()).isEqualTo(403);
        assertThat(http.get("/api/contas", null, ip).statusCode()).isEqualTo(401);
    }

    // ---- Efeitos de excluir --------------------------------------------------------------

    @Test
    void excluirUmaContaApagaAsConversasDelaEEncerraASessaoNaHora() throws Exception {
        long id = ler(criar(sessao("admin.bimbam"), nova("vai.sair", null, null))).get("id").asLong();
        String cookieDoUsuario = sessao("vai.sair");
        String thread = UUID.randomUUID().toString();
        http.post("/api/chat", Map.of("threadId", thread, "mensagem", "oi"), cookieDoUsuario, ip);
        long idDoOutro = contas.buscarPorUsuario("usuario.bimbam").orElseThrow().id();
        http.post("/api/chat", Map.of("threadId", thread, "mensagem", "oi"), sessao("usuario.bimbam"), ip);
        assertThat(conversasDaConta(id)).isEqualTo(1);
        // O banco é compartilhado com outras classes de teste: a conta do outro pode ter mais conversas.
        int conversasDoOutroAntes = conversasDaConta(idDoOutro);
        assertThat(conversasDoOutroAntes).isGreaterThanOrEqualTo(1);

        HttpResponse<String> exclusao = http.delete("/api/contas/" + id, sessao("admin.bimbam"), ip);

        assertThat(exclusao.statusCode()).isEqualTo(200);
        assertThat(ler(exclusao).get("mensagem").asText()).isEqualTo("Conta excluída.");
        assertThat(contas.buscarPorId(id)).isEmpty();
        assertThat(conversasDaConta(id)).as("as conversas da conta excluída saem junto").isZero();
        assertThat(conversasDaConta(idDoOutro)).as("as de outra conta ficam").isEqualTo(conversasDoOutroAntes);
        assertThat(http.get("/api/auth/eu", cookieDoUsuario, ip).statusCode()).as("a sessão dela morreu").isEqualTo(401);
    }

    private int conversasDaConta(long contaId) {
        return jdbc.sql("SELECT COUNT(*) FROM LG4JThread WHERE thread_name LIKE :prefixo")
                .param("prefixo", contaId + ":%")
                .query(Integer.class)
                .single();
    }
}
