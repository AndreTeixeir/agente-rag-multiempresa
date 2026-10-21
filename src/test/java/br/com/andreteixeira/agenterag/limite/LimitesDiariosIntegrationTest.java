package br.com.andreteixeira.agenterag.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.andreteixeira.agenterag.controller.DublesDoGrafoConfig;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import org.junit.jupiter.api.AfterAll;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Limites diários contra Postgres real (Testcontainers, porta dinâmica) e
 * Tomcat real: a migração V4, o incremento atômico com requisições
 * simultâneas, o rollback do total quando o IP recusa, a retenção, a virada do
 * dia e a obtenção do IP do cliente por {@code X-Forwarded-For}. O que não
 * precisa do banco está em {@link LimiteDiarioServiceTest}.
 * <p>
 * Limites deste teste: 20 perguntas por dia e 5 por IP (no lugar de 100 e 10
 * de produção, para o teste ser rápido). O relógio é um {@link RelogioMutavel}
 * parado em 21/10/2026 12:00 em São Paulo. O grafo e o modelo são os dublês de
 * {@link DublesDoGrafoConfig} e o {@link ServidorOpenAiFalso} — zero chamadas
 * ao Gemini e à Maritaca. Sem {@code @Tag("llm")}.
 */
@Testcontainers
@Import(DublesDoGrafoConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LimitesDiariosIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
                    DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("agenterag_test")
            .withUsername("rag")
            .withPassword("rag");

    private static final ServidorOpenAiFalso provedor = criarProvedor();

    // 2026-10-21 12:00 em São Paulo (UTC-3).
    private static final Instant INICIO = Instant.parse("2026-10-21T15:00:00Z");
    private static final LocalDate HOJE = LocalDate.of(2026, 10, 21);
    private static final RelogioMutavel RELOGIO = new RelogioMutavel(INICIO);

    // SHA-256 calculado fora do código, com sha256sum: printf '%s' '<ip>' | sha256sum
    private static final String CHAVE_DO_IP_203_0_113_10 =
            "ip:631f08140b24b7274d12df3c37a1a80ce5876dafd7007d772e0114fddf88b682";
    private static final String CHAVE_DO_IP_203_0_113_50 =
            "ip:3b5cd36857cbc87d59a841d2d375222d234ccf0317fb8656eceb0856c0c68048";
    private static final String CHAVE_DO_IP_127_0_0_1 =
            "ip:12ca17b49af2289436f303e0166030a21e525d266e209267433801a8fd4071a0";

    private static final ObjectMapper JSON = new ObjectMapper();

    private static ServidorOpenAiFalso criarProvedor() {
        try {
            return new ServidorOpenAiFalso();
        } catch (IOException e) {
            throw new IllegalStateException("não subiu o servidor HTTP local do teste", e);
        }
    }

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("GEMINI_API_KEY", () -> "chave-fake-do-gemini-nao-usada");
        registry.add("MARITACA_API_KEY", () -> "chave-fake-da-maritaca-nao-usada");
        registry.add("app.resposta.chat.base-url", provedor::baseUrl);
        registry.add("app.demo.limites.perguntas-por-dia", () -> "20");
        registry.add("app.demo.limites.perguntas-por-ip-por-dia", () -> "5");
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
    private LimiteDiarioService servico;

    @Autowired
    private JdbcClient jdbc;

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void zerarEstado() {
        jdbc.sql("DELETE FROM limite_diario").update();
        RELOGIO.definir(INICIO);
        provedor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("resposta de teste"));
    }

    @AfterAll
    static void fecharProvedor() {
        provedor.close();
    }

    // ---- V4 ------------------------------------------------------------------------------

    @Test
    void migracaoV4CriaATabelaComChavePrimariaPorDiaEChave() {
        List<String> colunas = jdbc.sql("""
                        SELECT column_name FROM information_schema.columns
                        WHERE table_name = 'limite_diario' ORDER BY ordinal_position
                        """)
                .query(String.class)
                .list();
        List<String> chavePrimaria = jdbc.sql("""
                        SELECT kcu.column_name
                        FROM information_schema.table_constraints tc
                        JOIN information_schema.key_column_usage kcu
                          ON kcu.constraint_name = tc.constraint_name AND kcu.table_name = tc.table_name
                        WHERE tc.table_name = 'limite_diario' AND tc.constraint_type = 'PRIMARY KEY'
                        ORDER BY kcu.ordinal_position
                        """)
                .query(String.class)
                .list();

        assertThat(colunas).as("V4__limites_diarios.sql").containsExactly("dia", "chave", "contagem");
        assertThat(chavePrimaria).containsExactly("dia", "chave");
    }

    // ---- Atomicidade com requisições simultâneas -----------------------------------------

    @Test
    void incrementoConcorrenteNaoUltrapassaOLimiteTotal() throws Exception {
        List<String> ips = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            ips.add("10.1.0." + i);
        }

        Desfecho desfecho = registrarEmParalelo(ips);

        assertThat(desfecho.aceitas).as("o limite total é 20").isEqualTo(20);
        assertThat(desfecho.recusadasPorTotal).isEqualTo(40);
        assertThat(desfecho.recusadasPorIp).isZero();
        assertThat(desfecho.outrosErros).isEmpty();
        assertThat(contagemDoTotal()).isEqualTo(20);
        assertThat(quantidadeDeContadoresDeIp()).as("só as 20 aceitas criaram contador de IP").isEqualTo(20);
    }

    @Test
    void incrementoConcorrenteDoMesmoIpNaoUltrapassaOLimiteDoIpENemDeixaOTotalAlemDoAceito() throws Exception {
        List<String> ips = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            ips.add("203.0.113.99");
        }

        Desfecho desfecho = registrarEmParalelo(ips);

        assertThat(desfecho.aceitas).as("o limite por IP é 5").isEqualTo(5);
        assertThat(desfecho.recusadasPorIp).isEqualTo(35);
        assertThat(desfecho.recusadasPorTotal).isZero();
        assertThat(desfecho.outrosErros).isEmpty();
        assertThat(contagemDoTotal())
                .as("as 35 recusadas pelo IP desfizeram o incremento do total: ele só conta as 5 aceitas")
                .isEqualTo(5);
        assertThat(quantidadeDeContadoresDeIp()).isEqualTo(1);
    }

    @Test
    void recusaPorTotalNaoCriaContadorParaOIpRecusado() {
        for (int ip = 1; ip <= 4; ip++) {
            for (int i = 0; i < 5; i++) {
                servico.registrarPergunta("10.2.0." + ip);
            }
        }

        assertThatThrownBy(() -> servico.registrarPergunta("10.2.0.99"))
                .isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> assertThat(e.tipo())
                        .isEqualTo(LimiteDiarioExcedidoException.Tipo.TOTAL));

        assertThat(contagemDoTotal()).isEqualTo(20);
        assertThat(quantidadeDeContadoresDeIp()).as("o 5º IP, recusado pelo total, não ganhou contador").isEqualTo(4);
    }

    // ---- Virada do dia e retenção --------------------------------------------------------

    @Test
    void diaNovoTemContadorNovoOTotalAnteriorFicaGuardadoEOIpAnteriorSai() {
        for (int i = 0; i < 5; i++) {
            servico.registrarPergunta("10.3.0.1");
        }
        assertThatThrownBy(() -> servico.registrarPergunta("10.3.0.1")).isInstanceOf(LimiteDiarioExcedidoException.class);

        // Mesmo horário do dia seguinte: 22/10 em São Paulo. O IP, recusado ontem, volta a ser aceito.
        RELOGIO.definir(INICIO.plusSeconds(24 * 60 * 60));
        servico.registrarPergunta("10.3.0.1");

        assertThat(linhasDoBanco()).contains("2026-10-21|total|5", "2026-10-22|total|1");
        assertThat(linhasDoBanco())
                .filteredOn(linha -> linha.contains("|ip:"))
                .as("a primeira pergunta do dia 22 apagou o contador de IP do dia 21; só o do dia 22 ficou")
                .hasSize(1)
                .allMatch(linha -> linha.startsWith("2026-10-22|ip:"));
    }

    @Test
    void limpezaApagaIpsDeDiasAnterioresEMantemOTotalPorSeteDias() {
        semear(HOJE.minusDays(1), "ip:aaa", 3);
        semear(HOJE, "ip:bbb", 1);
        semear(HOJE.minusDays(1), "total", 50);
        semear(HOJE.minusDays(7), "total", 10);
        semear(HOJE.minusDays(8), "total", 9);
        semear(HOJE.minusDays(8), "ip:ccc", 1);

        servico.limparAntigosAgendado();

        assertThat(linhasDoBanco())
                .as("IP: só o dia corrente. Total: até 7 dias (21/10 - 7 = 14/10 fica, 13/10 sai)")
                .containsExactly(
                        "2026-10-14|total|10",
                        "2026-10-20|total|50",
                        "2026-10-21|ip:bbb|1");
    }

    // ---- IP do cliente, num Tomcat real --------------------------------------------------

    @Test
    void ipDoClienteVemDoXForwardedForQuandoAConexaoEhDeUmProxyInterno() throws Exception {
        HttpResponse<String> resposta = postarChat("203.0.113.10");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(chavesDoBanco()).containsExactlyInAnyOrder("total", CHAVE_DO_IP_203_0_113_10);
        assertThat(chavesDoBanco()).noneMatch(chave -> chave.contains("203.0.113.10"));
    }

    @Test
    void xForwardedForForjadoPeloClienteNaoEngana() throws Exception {
        // O cliente forjou 198.51.100.99; o proxy confiável (loopback, aqui) acrescentou o IP real
        // 203.0.113.50 à direita. Vale o primeiro IP da direita que não seja proxy interno.
        HttpResponse<String> resposta = postarChat("198.51.100.99, 203.0.113.50");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(chavesDoBanco()).containsExactlyInAnyOrder("total", CHAVE_DO_IP_203_0_113_50);
    }

    @Test
    void semXForwardedForUsaOIpDaPropriaConexao() throws Exception {
        HttpResponse<String> resposta = postarChat(null);

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(chavesDoBanco()).containsExactlyInAnyOrder("total", CHAVE_DO_IP_127_0_0_1);
    }

    // ---- 429 de ponta a ponta ------------------------------------------------------------

    @Test
    void limitePorIpRetorna429ComAMensagemDoIpEOutroIpSegueLivre() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(postarChat("203.0.113.20").statusCode()).isEqualTo(200);
        }

        HttpResponse<String> recusada = postarChat("203.0.113.20");

        assertThat(recusada.statusCode()).isEqualTo(429);
        assertThat(erroDo(recusada))
                .isEqualTo("Você atingiu o limite de perguntas de hoje. Ele volta à meia-noite (horário de Brasília).");
        assertThat(postarChat("203.0.113.21").statusCode()).as("outro IP não é afetado").isEqualTo(200);
        assertThat(contagemDoTotal()).as("a recusada não contou").isEqualTo(6);
    }

    @Test
    void limiteTotalRetorna429ComAMensagemDaDemoSemChamarOModelo() throws Exception {
        for (int ip = 1; ip <= 4; ip++) {
            for (int i = 0; i < 5; i++) {
                assertThat(postarChat("203.0.113." + (100 + ip)).statusCode()).isEqualTo(200);
            }
        }
        int chamadasAoModeloAntes = provedor.requisicoes();

        HttpResponse<String> recusada = postarChat("203.0.113.200");

        assertThat(recusada.statusCode()).isEqualTo(429);
        assertThat(erroDo(recusada))
                .isEqualTo("A demo atingiu o limite de perguntas de hoje. Ele volta à meia-noite (horário de Brasília).");
        assertThat(provedor.requisicoes()).as("pergunta recusada não chega ao modelo").isEqualTo(chamadasAoModeloAntes);
        assertThat(contagemDoTotal()).isEqualTo(20);
    }

    // ---- apoio ---------------------------------------------------------------------------

    private HttpResponse<String> postarChat(String xForwardedFor) throws Exception {
        String corpo = JSON.writeValueAsString(Map.of(
                "threadId", UUID.randomUUID().toString(), "mensagem", "pergunta de teste", "empresaEscolhida", "bimbam"));
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/chat"))
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(corpo));
        if (xForwardedFor != null) {
            requisicao.header("X-Forwarded-For", xForwardedFor);
        }
        return http.send(requisicao.build(), BodyHandlers.ofString());
    }

    private static String erroDo(HttpResponse<String> resposta) throws IOException {
        return JSON.readTree(resposta.body()).get("erro").asText();
    }

    private Desfecho registrarEmParalelo(List<String> ips) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(ips.size());
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Throwable>> futuros = new ArrayList<>();
        try {
            for (String ip : ips) {
                Callable<Throwable> tarefa = () -> {
                    largada.await();
                    try {
                        servico.registrarPergunta(ip);
                        return null;
                    } catch (Throwable e) {
                        return e;
                    }
                };
                futuros.add(executor.submit(tarefa));
            }
            largada.countDown();

            Desfecho desfecho = new Desfecho();
            for (Future<Throwable> futuro : futuros) {
                desfecho.registrar(futuro.get(60, TimeUnit.SECONDS));
            }
            return desfecho;
        } finally {
            executor.shutdownNow();
        }
    }

    private static final class Desfecho {

        int aceitas;
        int recusadasPorTotal;
        int recusadasPorIp;
        final List<Throwable> outrosErros = new ArrayList<>();

        void registrar(Throwable erro) {
            if (erro == null) {
                aceitas++;
            } else if (erro instanceof LimiteDiarioExcedidoException limite) {
                if (limite.tipo() == LimiteDiarioExcedidoException.Tipo.TOTAL) {
                    recusadasPorTotal++;
                } else {
                    recusadasPorIp++;
                }
            } else {
                outrosErros.add(erro);
            }
        }
    }

    private void semear(LocalDate dia, String chave, int contagem) {
        jdbc.sql("INSERT INTO limite_diario (dia, chave, contagem) VALUES (:dia, :chave, :contagem)")
                .param("dia", dia)
                .param("chave", chave)
                .param("contagem", contagem)
                .update();
    }

    private int contagemDoTotal() {
        return jdbc.sql("SELECT COALESCE(SUM(contagem), 0) FROM limite_diario WHERE chave = 'total'")
                .query(Integer.class)
                .single();
    }

    private int quantidadeDeContadoresDeIp() {
        return jdbc.sql("SELECT COUNT(*) FROM limite_diario WHERE chave LIKE 'ip:%'")
                .query(Integer.class)
                .single();
    }

    private List<String> chavesDoBanco() {
        return jdbc.sql("SELECT chave FROM limite_diario").query(String.class).list();
    }

    private List<String> linhasDoBanco() {
        return jdbc.sql("SELECT dia || '|' || chave || '|' || contagem FROM limite_diario ORDER BY dia, chave")
                .query(String.class)
                .list();
    }
}
