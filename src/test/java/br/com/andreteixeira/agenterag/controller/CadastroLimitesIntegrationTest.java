package br.com.andreteixeira.agenterag.controller;

import static br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste.erro;
import static br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste.ler;
import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste;
import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.apoio.SessaoDeTeste;
import br.com.andreteixeira.agenterag.conta.ContaRepository;
import br.com.andreteixeira.agenterag.conta.SuperadminReal;
import br.com.andreteixeira.agenterag.limite.RelogioMutavel;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O teto de cadastros de contas — 5 por IP e 50 no total por dia, os valores de produção —, contra a
 * aplicação inteira: a recusa com 429 e mensagem própria, a exclusão que não devolve saldo, a recusa
 * por validação que não gasta vaga, a virada do dia (relógio sob controle do teste), o paralelismo e
 * o superadmin real, que fica fora do teto. Zero chamadas pagas.
 */
@Import(CadastroLimitesIntegrationTest.RelogioDeTesteConfig.class)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.superadmin.usuario=chefe.real", "app.superadmin.senha=senha-real-falsa-1"})
class CadastroLimitesIntegrationTest {

    private static final Instant INICIO = Instant.parse("2026-10-21T15:00:00Z"); // 12:00 em São Paulo
    private static final RelogioMutavel RELOGIO = new RelogioMutavel(INICIO);
    private static final AtomicInteger PROXIMO_USUARIO = new AtomicInteger(1);

    private static final String MENSAGEM_DO_IP =
            "Você atingiu o limite de cadastros de hoje. Ele volta à meia-noite (horário de Brasília).";
    private static final String MENSAGEM_DO_TOTAL =
            "A demo atingiu o limite de cadastros de hoje. Ele volta à meia-noite (horário de Brasília).";

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        PostgresDeTeste.registrar(registry);
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

    @BeforeEach
    void zerarOEstado() {
        RELOGIO.definir(INICIO);
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
        jdbc.sql("DELETE FROM limite_diario WHERE chave LIKE 'cadastro%'").update();
        superadminReal.configurar();
        http = new ClienteHttpDeTeste(porta);
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
        jdbc.sql("DELETE FROM limite_diario WHERE chave LIKE 'cadastro%'").update();
    }

    private String sessao(String usuario) {
        return SessaoDeTeste.NOME_DO_COOKIE + "=" + tokens.emitir(contas.buscarPorUsuario(usuario).orElseThrow().id());
    }

    private HttpResponse<String> cadastrar(String usuarioQueCadastra, String ip) {
        return cadastrar(usuarioQueCadastra, ip, "conta" + PROXIMO_USUARIO.getAndIncrement(), "senha-de-teste-1");
    }

    private HttpResponse<String> cadastrar(String usuarioQueCadastra, String ip, String novoUsuario, String senha) {
        return http.post(
                "/api/contas",
                Map.of("usuario", novoUsuario, "senha", senha, "papel", "USUARIO", "empresa", "bimbam"),
                sessao(usuarioQueCadastra),
                ip);
    }

    private int contasNaoSemeadas() {
        return jdbc.sql("SELECT COUNT(*) FROM conta WHERE NOT semeada AND NOT superadmin_real").query(Integer.class).single();
    }

    private int contagem(String chave) {
        return jdbc.sql("SELECT COALESCE(SUM(contagem), 0) FROM limite_diario WHERE chave = :chave")
                .param("chave", chave)
                .query(Integer.class)
                .single();
    }

    // ---- Por IP --------------------------------------------------------------------------

    @Test
    void oSextoCadastroDoMesmoIpRecebe429ComAMensagemDoIp() {
        for (int i = 1; i <= 5; i++) {
            assertThat(cadastrar("admin.bimbam", "198.51.100.1").statusCode()).as("cadastro " + i).isEqualTo(201);
        }

        HttpResponse<String> recusado = cadastrar("admin.bimbam", "198.51.100.1");

        assertThat(recusado.statusCode()).isEqualTo(429);
        assertThat(erro(recusado)).isEqualTo(MENSAGEM_DO_IP);
        assertThat(contasNaoSemeadas()).as("a recusada não criou conta").isEqualTo(5);
        assertThat(contagem("cadastro-total")).as("nem gastou o total").isEqualTo(5);
        assertThat(cadastrar("admin.bimbam", "198.51.100.2").statusCode()).as("outro IP segue livre").isEqualTo(201);
    }

    @Test
    void oTetoEDoIpEntaoContasDiferentesNoMesmoIpDividemAsCincoVagas() {
        for (int i = 0; i < 3; i++) {
            assertThat(cadastrar("admin.bimbam", "198.51.100.3").statusCode()).isEqualTo(201);
        }
        for (int i = 0; i < 2; i++) {
            assertThat(cadastrar("superadmin.demo", "198.51.100.3").statusCode()).isEqualTo(201);
        }

        assertThat(cadastrar("admin.santopegasus", "198.51.100.3").statusCode()).isEqualTo(429);
    }

    @Test
    void excluirUmaContaNaoDevolveAVaga() {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> criada = cadastrar("admin.bimbam", "198.51.100.4");
            assertThat(criada.statusCode()).isEqualTo(201);
            ids.add(ler(criada).get("id").asLong());
        }

        HttpResponse<String> exclusao = http.delete("/api/contas/" + ids.get(0), sessao("admin.bimbam"), "198.51.100.4");
        assertThat(exclusao.statusCode()).isEqualTo(200);

        assertThat(cadastrar("admin.bimbam", "198.51.100.4").statusCode()).as("o saldo não voltou").isEqualTo(429);
        assertThat(contagem("cadastro-total")).isEqualTo(5);
    }

    @Test
    void cadastroRecusadoPorValidacaoNaoGastaVaga() {
        for (int i = 0; i < 4; i++) {
            assertThat(cadastrar("admin.bimbam", "198.51.100.5").statusCode()).isEqualTo(201);
        }
        String repetido = ler(cadastrar("admin.bimbam", "198.51.100.6")).get("usuario").asText();
        long antes = contagem("cadastro-total");

        assertThat(cadastrar("admin.bimbam", "198.51.100.5", repetido, "senha-de-teste-1").statusCode())
                .as("usuário em uso").isEqualTo(400);
        assertThat(cadastrar("admin.bimbam", "198.51.100.5", "senha.curta", "123").statusCode())
                .as("senha curta").isEqualTo(400);
        assertThat(cadastrar("admin.bimbam", "198.51.100.5", "ab", "senha-de-teste-1").statusCode())
                .as("usuário inválido").isEqualTo(400);

        assertThat(contagem("cadastro-total")).as("nenhuma das três gastou vaga").isEqualTo(antes);
        assertThat(cadastrar("admin.bimbam", "198.51.100.5").statusCode()).as("a 5ª vaga do IP ainda estava lá").isEqualTo(201);
        assertThat(cadastrar("admin.bimbam", "198.51.100.5").statusCode()).isEqualTo(429);
    }

    @Test
    void oUsuarioComumEOSemLoginNaoGastamVagaDeCadastro() {
        http.post("/api/contas", Map.of("usuario", "nao.pode", "senha", "senha-de-teste-1"), sessao("usuario.bimbam"), "198.51.100.7");
        http.post("/api/contas", Map.of("usuario", "nao.pode", "senha", "senha-de-teste-1"), null, "198.51.100.7");

        assertThat(contagem("cadastro-total")).isZero();
    }

    @Test
    void cadastrosEmParaleloDoMesmoIpNuncaPassamDeCinco() throws Exception {
        int tentativas = 20;
        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        String sessao = sessao("admin.bimbam");
        List<Future<Integer>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < tentativas; i++) {
                String usuario = "paralela" + i;
                Callable<Integer> tarefa = () -> {
                    largada.await();
                    return http.post(
                                    "/api/contas",
                                    Map.of("usuario", usuario, "senha", "senha-de-teste-1"),
                                    sessao,
                                    "198.51.100.8")
                            .statusCode();
                };
                futuros.add(executor.submit(tarefa));
            }
            largada.countDown();

            int criadas = 0;
            int recusadas = 0;
            for (Future<Integer> futuro : futuros) {
                int status = futuro.get(60, TimeUnit.SECONDS);
                if (status == 201) {
                    criadas++;
                } else if (status == 429) {
                    recusadas++;
                }
            }

            assertThat(criadas).isEqualTo(5);
            assertThat(recusadas).isEqualTo(15);
            assertThat(contasNaoSemeadas()).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- No total ------------------------------------------------------------------------

    @Test
    void oTotalDe50PorDiaTambemTemTetoEAMensagemEDaDemo() {
        for (int ip = 1; ip <= 10; ip++) {
            for (int i = 0; i < 5; i++) {
                assertThat(cadastrar("superadmin.demo", "192.0.2." + ip).statusCode()).isEqualTo(201);
            }
        }
        assertThat(contagem("cadastro-total")).isEqualTo(50);

        HttpResponse<String> recusado = cadastrar("superadmin.demo", "192.0.2.200");

        assertThat(recusado.statusCode()).isEqualTo(429);
        assertThat(erro(recusado)).isEqualTo(MENSAGEM_DO_TOTAL);
        assertThat(contasNaoSemeadas()).isEqualTo(50);
        assertThat(contagem("cadastro-total")).as("a recusada não contou").isEqualTo(50);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM limite_diario WHERE chave LIKE 'cadastro-ip:%'")
                        .query(Integer.class)
                        .single())
                .as("o 11º IP, recusado pelo total, não ganhou contador")
                .isEqualTo(10);
    }

    // ---- Virada do dia -------------------------------------------------------------------

    @Test
    void umDiaNovoTemTetoNovo() {
        for (int i = 0; i < 5; i++) {
            cadastrar("admin.bimbam", "198.51.100.9");
        }
        assertThat(cadastrar("admin.bimbam", "198.51.100.9").statusCode()).isEqualTo(429);

        // Mesmo horário do dia seguinte em São Paulo. O token emitido antes venceria em 8 h: emite-se outro.
        RELOGIO.definir(INICIO.plus(Duration.ofDays(1)));

        assertThat(cadastrar("admin.bimbam", "198.51.100.9").statusCode()).isEqualTo(201);
    }

    // ---- O superadmin real fica de fora --------------------------------------------------

    @Test
    void oSuperadminRealCadastraSemTetoENaoGastaAsVagasDosOutros() {
        for (int i = 0; i < 8; i++) {
            assertThat(cadastrar("chefe.real", "198.51.100.10").statusCode()).as("cadastro " + i).isEqualTo(201);
        }

        assertThat(contagem("cadastro-total")).as("o real não conta").isZero();
        for (int i = 0; i < 5; i++) {
            assertThat(cadastrar("admin.bimbam", "198.51.100.10").statusCode())
                    .as("o admin do mesmo IP ainda tem as 5 vagas")
                    .isEqualTo(201);
        }
        assertThat(cadastrar("admin.bimbam", "198.51.100.10").statusCode()).isEqualTo(429);
    }

    // ---- O que vai para o banco ----------------------------------------------------------

    @Test
    void asChavesSaoOHashDoIpEOTotalSemOIpEmTexto() {
        cadastrar("admin.bimbam", "198.51.100.11");

        List<String> chaves = jdbc.sql("SELECT chave FROM limite_diario WHERE chave LIKE 'cadastro%'")
                .query(String.class)
                .list();

        assertThat(chaves)
                .hasSize(2)
                .anySatisfy(chave -> assertThat(chave).isEqualTo("cadastro-total"))
                .anySatisfy(chave -> assertThat(chave).matches("cadastro-ip:[0-9a-f]{64}").hasSizeLessThanOrEqualTo(80))
                .noneMatch(chave -> chave.contains("198.51.100.11"));
    }
}
