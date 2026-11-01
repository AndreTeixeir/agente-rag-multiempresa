package br.com.andreteixeira.agenterag.conta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.ClienteHttpDeTeste;
import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.apoio.SessaoDeTeste;
import br.com.andreteixeira.agenterag.controller.DublesDoGrafoConfig;
import br.com.andreteixeira.agenterag.grafo.ConversaRepository;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioRepository;
import br.com.andreteixeira.agenterag.limite.LimitesProperties;
import br.com.andreteixeira.agenterag.limite.RelogioMutavel;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.support.SimpleTriggerContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionOperations;

/**
 * O reset diário contra a aplicação inteira, com o relógio sob controle do teste: o que apaga e o que
 * mantém, que roda uma vez por dia (inclusive no primeiro boot do dia), que a meia-noite é a do fuso de
 * São Paulo, que uma falha não apaga nada, e — o ponto da seção 4.6 do briefing — que depois do reset a
 * próxima mensagem de uma conversa antiga começa uma conversa nova, sem erro. Zero chamadas pagas.
 */
@Import({DublesDoGrafoConfig.class, ResetDiarioIntegrationTest.RelogioDeTesteConfig.class})
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.superadmin.usuario=chefe.real", "app.superadmin.senha=senha-real-falsa-1"})
class ResetDiarioIntegrationTest {

    // 12:00 em São Paulo de 10/11/2026 (UTC-3, sem horário de verão).
    private static final Instant INICIO = Instant.parse("2026-11-10T15:00:00Z");
    private static final RelogioMutavel RELOGIO = new RelogioMutavel(INICIO);
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
    private ResetDiarioService reset;

    @Autowired
    private ContaRepository contas;

    @Autowired
    private SuperadminReal superadminReal;

    @Autowired
    private PasswordEncoder encoder;

    @Autowired
    private TokenService tokens;

    @Autowired
    private LimiteDiarioRepository limites;

    @Autowired
    private LimitesProperties propriedades;

    @Autowired
    private TransactionOperations transacao;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private JdbcClient jdbc;

    @LocalServerPort
    private int porta;

    private ClienteHttpDeTeste http;
    private String ip;
    private ListAppender<ILoggingEvent> log;
    private Logger logger;

    @BeforeEach
    void prepararOCenario() {
        RELOGIO.definir(INICIO);
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
        jdbc.sql("DELETE FROM LG4JThread").update();
        jdbc.sql("DELETE FROM limite_diario WHERE chave = 'reset-diario'").update();
        superadminReal.configurar();
        provedor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("resposta de teste"));
        http = new ClienteHttpDeTeste(porta);
        ip = "203.0.113." + PROXIMO_IP.getAndIncrement();
        log = new ListAppender<>();
        log.start();
        logger = (Logger) LoggerFactory.getLogger(ResetDiarioService.class);
        logger.addAppender(log);
    }

    @AfterEach
    void limpar() {
        logger.detachAppender(log);
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
        jdbc.sql("DELETE FROM LG4JThread").update();
        jdbc.sql("DELETE FROM limite_diario WHERE chave = 'reset-diario'").update();
    }

    @AfterAll
    static void fecharProvedor() {
        provedor.close();
    }

    private long id(String usuario) {
        return contas.buscarPorUsuario(usuario).orElseThrow().id();
    }

    private long criar(String usuario, Papel papel, String empresa, Long criadaPor) {
        return contas.inserir(usuario, encoder.encode("senha-de-teste-1"), papel, empresa, false, false, criadaPor);
    }

    private String sessao(String usuario) {
        return SessaoDeTeste.NOME_DO_COOKIE + "=" + tokens.emitir(id(usuario));
    }

    private void passarParaOProximoDia() {
        RELOGIO.definir(RELOGIO.instant().plus(Duration.ofDays(1)));
    }

    private List<String> usuarios() {
        return jdbc.sql("SELECT usuario FROM conta ORDER BY usuario").query(String.class).list();
    }

    private int contar(String tabela) {
        return jdbc.sql("SELECT COUNT(*) FROM " + tabela).query(Integer.class).single();
    }

    private HttpResponse<String> conversar(String cookie, String thread, String empresa) {
        Map<String, String> corpo = new java.util.HashMap<>();
        corpo.put("threadId", thread);
        corpo.put("mensagem", "pergunta");
        if (empresa != null) {
            corpo.put("empresaEscolhida", empresa);
        }
        return http.post("/api/chat", corpo, cookie, ip);
    }

    // ---- O que apaga e o que mantém ------------------------------------------------------

    @Test
    void apagaAsContasDeDemonstracaoEMantemAsProtegidas() {
        long admin = id("admin.bimbam");
        long demo = id("superadmin.demo");
        long real = id("chefe.real");
        long filhaDoAdmin = criar("filha.do.admin", Papel.USUARIO, "bimbam", admin);
        criar("filha.da.filha", Papel.USUARIO, "bimbam", filhaDoAdmin);
        criar("admin.do.demo", Papel.ADMIN, "bimbam", demo);
        criar("orfa", Papel.USUARIO, "mercado-central", null);
        criar("criada.pela.real", Papel.USUARIO, "bimbam", real);
        criar("admin.da.real", Papel.ADMIN, "santo-pegasus", real);
        passarParaOProximoDia();

        assertThat(reset.executarSeNovoDia()).isTrue();

        assertThat(usuarios())
                .containsExactly(
                        "admin.bimbam",
                        "admin.da.real",
                        "admin.mercadocentral",
                        "admin.santopegasus",
                        "chefe.real",
                        "criada.pela.real",
                        "superadmin.demo",
                        "usuario.bimbam",
                        "usuario.mercadocentral",
                        "usuario.santopegasus");
    }

    @Test
    void asSemeadasContinuamEntrandoComASenhaPublica() {
        passarParaOProximoDia();
        reset.executarSeNovoDia();

        for (String usuario : List.of("superadmin.demo", "admin.bimbam", "usuario.mercadocentral", "admin.santopegasus")) {
            HttpResponse<String> login =
                    http.post("/api/auth/login", Map.of("usuario", usuario, "senha", "sabia-demo"), null, ip);
            assertThat(login.statusCode()).as(usuario).isEqualTo(200);
        }
    }

    @Test
    void apagaTodasAsConversasEOsCheckpointsDelas() {
        conversar(sessao("usuario.bimbam"), "t-bimbam", null);
        conversar(sessao("admin.santopegasus"), "t-pegasus", null);
        conversar(sessao("chefe.real"), "t-real", "bimbam");
        conversar(sessao("usuario.bimbam"), "t-bimbam", null);
        assertThat(contar("LG4JThread")).isEqualTo(3);
        assertThat(contar("LG4JCheckpoint")).isGreaterThanOrEqualTo(3);
        passarParaOProximoDia();

        assertThat(reset.executarSeNovoDia()).isTrue();

        assertThat(contar("LG4JThread")).isZero();
        assertThat(contar("LG4JCheckpoint")).as("saem pelo ON DELETE CASCADE").isZero();
    }

    @Test
    void registraUmaLinhaInfoComAsContagens() {
        criar("a.apagar.1", Papel.USUARIO, "bimbam", null);
        criar("a.apagar.2", Papel.USUARIO, "bimbam", null);
        conversar(sessao("usuario.bimbam"), "t1", null);
        conversar(sessao("usuario.santopegasus"), "t2", null);
        conversar(sessao("usuario.mercadocentral"), "t3", null);
        passarParaOProximoDia();

        reset.executarSeNovoDia();

        assertThat(log.list)
                .filteredOn(evento -> evento.getLevel() == Level.INFO)
                .singleElement()
                .satisfies(evento -> assertThat(evento.getFormattedMessage())
                        .isEqualTo("Reset diário de 2026-11-11: 2 contas de demonstração e 3 conversas apagadas."));
    }

    // ---- Uma vez por dia -----------------------------------------------------------------

    @Test
    void noMesmoDiaOResetSoRodaUmaVezEOQueSeCriaDepoisFica() {
        passarParaOProximoDia();
        assertThat(reset.executarSeNovoDia()).isTrue();
        criar("criada.depois", Papel.USUARIO, "bimbam", null);

        assertThat(reset.executarSeNovoDia()).as("segunda chamada no mesmo dia").isFalse();
        assertThat(reset.executarSeNovoDia()).isFalse();

        assertThat(usuarios()).contains("criada.depois");
    }

    @Test
    void oPrimeiroBootDoDiaRodaOResetQuandoAMeiaNoiteFoiPerdida() {
        criar("de.ontem", Papel.USUARIO, "bimbam", null);
        conversar(sessao("usuario.bimbam"), "t1", null);
        // O app ficou fora do ar na meia-noite: o agendamento não rodou. Na manhã seguinte ele sobe.
        RELOGIO.definir(Instant.parse("2026-11-11T11:00:00Z"));

        reset.run(null);

        assertThat(usuarios()).doesNotContain("de.ontem");
        assertThat(contar("LG4JThread")).isZero();
    }

    @Test
    void reiniciarOAppNoMesmoDiaNaoApagaDeNovo() {
        passarParaOProximoDia();
        reset.run(null);
        criar("criada.depois.do.boot", Papel.USUARIO, "bimbam", null);

        reset.run(null);

        assertThat(usuarios()).contains("criada.depois.do.boot");
    }

    @Test
    void aMeiaNoiteEADeSaoPauloENaoADeUtc() {
        // 23:59:59 de 09/11 em São Paulo (02:59:59Z de 10/11): ainda é o dia 09.
        RELOGIO.definir(Instant.parse("2026-11-10T02:59:59Z"));
        assertThat(reset.executarSeNovoDia()).isTrue();
        assertThat(limites.reivindicar(LocalDate.of(2026, 11, 9), "reset-diario")).as("o dia 09 foi o reivindicado").isFalse();

        // 00:00:00 de 10/11 em São Paulo (03:00:00Z): outro dia.
        RELOGIO.definir(Instant.parse("2026-11-10T03:00:00Z"));
        assertThat(reset.executarSeNovoDia()).isTrue();
        assertThat(reset.executarSeNovoDia()).isFalse();
    }

    @Test
    void duasInstanciasNoMesmoDiaSoUmaApaga() throws Exception {
        passarParaOProximoDia();
        int tentativas = 10;
        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Boolean>> futuros = new ArrayList<>();
        try {
            for (int i = 0; i < tentativas; i++) {
                Callable<Boolean> tarefa = () -> {
                    largada.await();
                    return reset.executarSeNovoDia();
                };
                futuros.add(executor.submit(tarefa));
            }
            largada.countDown();

            int quemRodou = 0;
            for (Future<Boolean> futuro : futuros) {
                if (futuro.get(60, TimeUnit.SECONDS)) {
                    quemRodou++;
                }
            }

            assertThat(quemRodou).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    // ---- Falha ---------------------------------------------------------------------------

    @Test
    void seApagarFalharNadaEhApagadoEAProximaTentativaRoda() {
        criar("tem.que.sobreviver", Papel.USUARIO, "bimbam", null);
        conversar(sessao("usuario.bimbam"), "t1", null);
        passarParaOProximoDia();
        ConversaRepository quebrado = new ConversaRepository(jdbc) {
            @Override
            public int apagarTodas() {
                throw new IllegalStateException("falha simulada ao apagar as conversas");
            }
        };
        ResetDiarioService comFalha = new ResetDiarioService(limites, contas, quebrado, transacao, propriedades, RELOGIO);

        assertThat(comFalha.executarSeNovoDia()).isFalse();

        assertThat(usuarios()).as("a transação desfez o apagar das contas").contains("tem.que.sobreviver");
        assertThat(contar("LG4JThread")).isEqualTo(1);
        assertThat(log.list).filteredOn(e -> e.getLevel() == Level.ERROR).hasSize(1);

        assertThat(reset.executarSeNovoDia()).as("a reivindicação também foi desfeita: dá para tentar de novo").isTrue();
        assertThat(usuarios()).doesNotContain("tem.que.sobreviver");
    }

    // ---- Depois do reset, a conversa continua sem erro (briefing, seção 4.6) --------------

    @Test
    void depoisDoResetAMensagemDeUmaConversaAntigaComecaUmaNovaSemErro() throws Exception {
        String sessao = sessao("usuario.bimbam");
        String thread = UUID.randomUUID().toString();
        assertThat(conversar(sessao, thread, null).statusCode()).isEqualTo(200);
        int antes = provedor.requisicoes();
        assertThat(conversar(sessao, thread, null).statusCode()).isEqualTo(200);
        assertThat(provedor.requisicoes() - antes).as("antes do reset há histórico: reescrita + resposta").isEqualTo(2);
        passarParaOProximoDia();
        assertThat(reset.executarSeNovoDia()).isTrue();
        assertThat(contar("LG4JThread")).isZero();
        String sessaoDepois = sessao("usuario.bimbam");

        antes = provedor.requisicoes();
        HttpResponse<String> depois = conversar(sessaoDepois, thread, null);

        assertThat(depois.statusCode()).as("a conversa apagada não vira erro").isEqualTo(200);
        assertThat(ClienteHttpDeTeste.ler(depois).get("empresa").asText()).isEqualTo("bimbam");
        assertThat(provedor.requisicoes() - antes)
                .as("começou uma conversa nova: sem histórico, sem reescrita, só a resposta")
                .isEqualTo(1);
        assertThat(contar("LG4JThread")).as("a thread foi recriada").isEqualTo(1);
    }

    @Test
    void oSuperadminQueMandaAEmpresaDeNovoRecomecaSemErroEQuemNaoMandaRecebe400() throws Exception {
        String sessao = sessao("superadmin.demo");
        String thread = UUID.randomUUID().toString();
        conversar(sessao, thread, "santo-pegasus");
        passarParaOProximoDia();
        reset.executarSeNovoDia();
        String sessaoDepois = sessao("superadmin.demo");

        HttpResponse<String> comEmpresa = conversar(sessaoDepois, thread, "santo-pegasus");
        assertThat(comEmpresa.statusCode()).isEqualTo(200);
        assertThat(ClienteHttpDeTeste.ler(comEmpresa).get("empresa").asText()).isEqualTo("santo-pegasus");

        // Sem a empresa, numa conversa que o reset apagou: o servidor não adivinha — por isso a interface
        // do superadmin manda sempre a empresa escolhida.
        String outraThread = UUID.randomUUID().toString();
        conversar(sessaoDepois, outraThread, "bimbam");
        passarParaOProximoDia();
        reset.executarSeNovoDia();
        HttpResponse<String> semEmpresa = conversar(sessao("superadmin.demo"), outraThread, null);
        assertThat(semEmpresa.statusCode()).isEqualTo(400);
        assertThat(ClienteHttpDeTeste.erro(semEmpresa)).contains("precisa informar a empresa");
    }

    @Test
    void asSessoesDasContasApagadasDeixamDeValerMesmoComOTokenDentroDaValidade() {
        criar("vai.sumir", Papel.USUARIO, "bimbam", null);
        // 23:00 de 10/11 em São Paulo: o token (8 h) ainda vale depois da meia-noite.
        RELOGIO.definir(Instant.parse("2026-11-11T02:00:00Z"));
        String sessaoDela = sessao("vai.sumir");
        assertThat(http.get("/api/auth/eu", sessaoDela, ip).statusCode()).isEqualTo(200);

        // 00:30 de 11/11: já é outro dia, e o token ainda está dentro das 8 h.
        RELOGIO.definir(Instant.parse("2026-11-11T03:30:00Z"));
        assertThat(http.get("/api/auth/eu", sessaoDela, ip).statusCode()).as("antes do reset, vale").isEqualTo(200);
        assertThat(reset.executarSeNovoDia()).isTrue();

        assertThat(http.get("/api/auth/eu", sessaoDela, ip).statusCode())
                .as("o token não venceu, mas a conta foi apagada")
                .isEqualTo(401);
    }

    // ---- Agendamento ---------------------------------------------------------------------

    @Test
    void oAgendamentoDisparaEmPontoNaMeiaNoiteDeSaoPaulo() {
        ScheduledAnnotationBeanPostProcessor agendador = contexto.getBean(ScheduledAnnotationBeanPostProcessor.class);

        List<CronTask> crons = agendador.getScheduledTasks().stream()
                .map(tarefa -> tarefa.getTask())
                .filter(CronTask.class::isInstance)
                .map(CronTask.class::cast)
                .filter(cron -> cron.getRunnable().toString().contains("ResetDiarioService"))
                .toList();

        assertThat(crons).hasSize(1);
        assertThat(crons.get(0).getExpression()).isEqualTo("0 0 0 * * *");
        Instant proximo = crons.get(0)
                .getTrigger()
                .nextExecution(new SimpleTriggerContext(Clock.fixed(Instant.parse("2026-11-10T15:00:00Z"), ZoneOffset.UTC)));
        assertThat(proximo)
                .as("meia-noite de São Paulo (UTC-3) do dia seguinte")
                .isEqualTo(Instant.parse("2026-11-11T03:00:00Z"));
    }
}
