package br.com.andreteixeira.agenterag.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Lógica do {@link LimiteDiarioService} sem banco: repositório em memória que
 * imita a semântica do SQL, relógio que anda sob controle do teste e uma
 * "transação" que desfaz o que foi gravado quando o corpo lança. O que só o
 * Postgres prova — atomicidade com requisições simultâneas e o rollback de
 * verdade — fica em {@code LimitesDiariosIntegrationTest}. Sem {@code @Tag("llm")}.
 */
class LimiteDiarioServiceTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    // SHA-256 calculado fora do código, com sha256sum: printf '%s' '203.0.113.5' | sha256sum
    private static final String SHA256_DE_203_0_113_5 = "440a628a0c975ea32d4db42ca94acebc975ab378b3ee2a692ccf2ecae6038bbd";
    // printf '%s' 'desconhecido' | sha256sum
    private static final String SHA256_DE_DESCONHECIDO = "f00aff1c8f41c0371ba2869205da730485e203e33c02cf7192a025afc6540ab6";

    // 2026-10-21 12:00 em São Paulo (UTC-3, sem horário de verão em 2026).
    private static final Instant MEIO_DIA_DE_21_DE_OUTUBRO = Instant.parse("2026-10-21T15:00:00Z");
    private static final LocalDate DIA_21 = LocalDate.of(2026, 10, 21);

    private final RepositorioEmMemoria repositorio = new RepositorioEmMemoria();
    private final TransacaoDeTeste transacao = new TransacaoDeTeste(repositorio);
    private final RelogioMutavel relogio = new RelogioMutavel(MEIO_DIA_DE_21_DE_OUTUBRO);

    private LimiteDiarioService servico(int perguntasPorDia, int perguntasPorIpPorDia) {
        return servico(perguntasPorDia, perguntasPorIpPorDia, 10);
    }

    private LimiteDiarioService servico(int perguntasPorDia, int perguntasPorIpPorDia, int tentativasDeLoginPorHora) {
        return servico(perguntasPorDia, perguntasPorIpPorDia, tentativasDeLoginPorHora, 50, 5);
    }

    private LimiteDiarioService servicoDeCadastros(int cadastrosPorDia, int cadastrosPorIpPorDia) {
        return servico(100, 10, 10, cadastrosPorDia, cadastrosPorIpPorDia);
    }

    private LimiteDiarioService servico(
            int perguntasPorDia,
            int perguntasPorIpPorDia,
            int tentativasDeLoginPorHora,
            int cadastrosPorDia,
            int cadastrosPorIpPorDia) {
        return new LimiteDiarioService(
                repositorio,
                transacao,
                new LimitesProperties(
                        perguntasPorDia,
                        perguntasPorIpPorDia,
                        SAO_PAULO,
                        1000,
                        tentativasDeLoginPorHora,
                        cadastrosPorDia,
                        cadastrosPorIpPorDia),
                relogio);
    }

    private static String chaveDoIp(String hashHex) {
        return "ip:" + hashHex;
    }

    @Test
    void aceitaPerguntasAteOLimiteTotalEDepoisRecusaSemContar() {
        LimiteDiarioService servico = servico(3, 10);
        servico.registrarPergunta("10.0.0.1");
        servico.registrarPergunta("10.0.0.2");
        servico.registrarPergunta("10.0.0.3");

        assertThatThrownBy(() -> servico.registrarPergunta("10.0.0.4"))
                .isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> assertThat(e.tipo())
                        .isEqualTo(LimiteDiarioExcedidoException.Tipo.TOTAL));

        assertThat(repositorio.contagem(DIA_21, "total")).as("a recusada não conta").isEqualTo(3);
        assertThat(repositorio.chavesDoDia(DIA_21)).as("o IP recusado pelo total nem chega a ter contador").hasSize(4);
    }

    @Test
    void aceitaPerguntasDeUmIpAteOLimiteEDepoisRecusaSemConsumirOTotal() {
        LimiteDiarioService servico = servico(100, 2);
        servico.registrarPergunta("203.0.113.5");
        servico.registrarPergunta("203.0.113.5");

        assertThatThrownBy(() -> servico.registrarPergunta("203.0.113.5"))
                .isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> assertThat(e.tipo())
                        .isEqualTo(LimiteDiarioExcedidoException.Tipo.IP));

        assertThat(repositorio.contagem(DIA_21, chaveDoIp(SHA256_DE_203_0_113_5))).isEqualTo(2);
        assertThat(repositorio.contagem(DIA_21, "total"))
                .as("a pergunta recusada pelo IP desfaz o incremento do total")
                .isEqualTo(2);
        assertThat(transacao.desfeitas()).as("a recusa pelo IP lança de dentro da transação").isEqualTo(1);
    }

    @Test
    void ipsDiferentesTemContadoresIndependentes() {
        LimiteDiarioService servico = servico(100, 1);
        servico.registrarPergunta("203.0.113.5");
        servico.registrarPergunta("198.51.100.7");

        assertThatThrownBy(() -> servico.registrarPergunta("203.0.113.5")).isInstanceOf(LimiteDiarioExcedidoException.class);
        assertThatCode(() -> servico.registrarPergunta("192.0.2.9")).doesNotThrowAnyException();

        assertThat(repositorio.contagem(DIA_21, "total")).isEqualTo(3);
    }

    @Test
    void ipNuncaEhGravadoEmTextoSoOHashSha256() {
        servico(100, 10).registrarPergunta("203.0.113.5");

        assertThat(repositorio.chavesDoDia(DIA_21))
                .containsExactlyInAnyOrder("total", chaveDoIp(SHA256_DE_203_0_113_5))
                .noneMatch(chave -> chave.contains("203.0.113.5"));
    }

    @Test
    void ipAusenteVaiParaUmaChaveFixaEmVezDeFalhar() {
        servico(100, 10).registrarPergunta(null);

        assertThat(repositorio.chavesDoDia(DIA_21)).contains(chaveDoIp(SHA256_DE_DESCONHECIDO));
    }

    @Test
    void odiaViraNaMeiaNoiteDoFusoConfiguradoENaDoUtc() {
        LimiteDiarioService servico = servico(100, 1);

        // 23:59:59 de 20/10 em São Paulo (02:59:59Z de 21/10): o dia ainda é 20.
        relogio.definir(Instant.parse("2026-10-21T02:59:59Z"));
        servico.registrarPergunta("203.0.113.5");
        assertThatThrownBy(() -> servico.registrarPergunta("203.0.113.5")).isInstanceOf(LimiteDiarioExcedidoException.class);

        // 00:00:00Z de 21/10 é meia-noite em UTC, mas ainda 21:00 de 20/10 em São Paulo: o dia não virou.
        relogio.definir(Instant.parse("2026-10-21T00:00:00Z"));
        assertThatThrownBy(() -> servico.registrarPergunta("203.0.113.5")).isInstanceOf(LimiteDiarioExcedidoException.class);

        // 00:00:00 de 21/10 em São Paulo (03:00:00Z): o dia virou e o contador do IP recomeça.
        relogio.definir(Instant.parse("2026-10-21T03:00:00Z"));
        assertThatCode(() -> servico.registrarPergunta("203.0.113.5")).doesNotThrowAnyException();

        assertThat(repositorio.contagem(LocalDate.of(2026, 10, 20), chaveDoIp(SHA256_DE_203_0_113_5))).isEqualTo(1);
        assertThat(repositorio.contagem(DIA_21, chaveDoIp(SHA256_DE_203_0_113_5))).isEqualTo(1);
        assertThat(repositorio.contagem(LocalDate.of(2026, 10, 20), "total")).isEqualTo(1);
        assertThat(repositorio.contagem(DIA_21, "total")).isEqualTo(1);
    }

    @Test
    void limiteZeroBloqueiaTodasAsPerguntas() {
        assertThatThrownBy(() -> servico(0, 10).registrarPergunta("203.0.113.5"))
                .isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> assertThat(e.tipo())
                        .isEqualTo(LimiteDiarioExcedidoException.Tipo.TOTAL));

        assertThatThrownBy(() -> servico(100, 0).registrarPergunta("203.0.113.5"))
                .isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> assertThat(e.tipo())
                        .isEqualTo(LimiteDiarioExcedidoException.Tipo.IP));

        assertThat(repositorio.chavesDoDia(DIA_21)).as("nada foi contado").isEmpty();
    }

    @Test
    void primeiraPerguntaDoDiaLimpaOsContadoresAntigosEAsSeguintesNao() {
        LimiteDiarioService servico = servico(100, 10);

        servico.registrarPergunta("203.0.113.5");
        servico.registrarPergunta("198.51.100.7");

        assertThat(repositorio.limpezasDeIp)
                .as("contagens de IP: só o dia corrente fica guardado")
                .containsExactly(DIA_21);
        assertThat(repositorio.limpezasGerais)
                .as("contagens do total: mais de 7 dias são apagadas")
                .containsExactly(LocalDate.of(2026, 10, 14));
    }

    @Test
    void limpezaAgendadaUsaOFusoParaDefinirOQueEhHoje() {
        // 23:30 de 20/10 em São Paulo, já 21/10 em UTC.
        relogio.definir(Instant.parse("2026-10-21T02:30:00Z"));

        servico(100, 10).limparAntigosAgendado();

        assertThat(repositorio.limpezasDeIp).containsExactly(LocalDate.of(2026, 10, 20));
        assertThat(repositorio.limpezasGerais).containsExactly(LocalDate.of(2026, 10, 13));
    }

    @Test
    void falhaNaLimpezaNaoDerrubaAPerguntaQueADisparou() {
        repositorio.falharNaLimpeza = true;

        assertThatCode(() -> servico(100, 10).registrarPergunta("203.0.113.5")).doesNotThrowAnyException();

        assertThat(repositorio.contagem(DIA_21, "total")).isEqualTo(1);
    }

    @Test
    void limitesNegativosOuFusoAusenteImpedemOStartup() {
        assertThatThrownBy(() -> new LimitesProperties(-1, 10, SAO_PAULO, 1000, 10, 50, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesProperties(100, -1, SAO_PAULO, 1000, 10, 50, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesProperties(100, 10, null, 1000, 10, 50, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesProperties(100, 10, SAO_PAULO, 0, 10, 50, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesProperties(100, 10, SAO_PAULO, 1000, -1, 50, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesProperties(100, 10, SAO_PAULO, 1000, 10, -1, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitesProperties(100, 10, SAO_PAULO, 1000, 10, 50, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Teto de cadastros de contas, por dia e por IP --------------------------------------

    private static final String CHAVE_DE_CADASTRO_DO_IP_5 = "cadastro-ip:" + SHA256_DE_203_0_113_5;

    private static void assertRecusadoComo(
            Runnable acao, LimiteDiarioExcedidoException.Tipo tipo, LimiteDiarioExcedidoException.Assunto assunto) {
        assertThatThrownBy(acao::run).isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> {
            assertThat(e.tipo()).isEqualTo(tipo);
            assertThat(e.assunto()).isEqualTo(assunto);
        });
    }

    @Test
    void aSextaTentativaDeCadastroDoMesmoIpEhRecusadaPeloIp() {
        LimiteDiarioService servico = servicoDeCadastros(50, 5);
        for (int i = 0; i < 5; i++) {
            servico.registrarCadastro("203.0.113.5");
        }

        assertRecusadoComo(
                () -> servico.registrarCadastro("203.0.113.5"),
                LimiteDiarioExcedidoException.Tipo.IP,
                LimiteDiarioExcedidoException.Assunto.CADASTROS);
        assertThat(repositorio.contagem(DIA_21, "cadastro-total")).as("a recusada pelo IP desfez o total").isEqualTo(5);
        assertThat(repositorio.contagem(DIA_21, CHAVE_DE_CADASTRO_DO_IP_5)).isEqualTo(5);
    }

    @Test
    void oTotalDeCadastrosDoDiaTambemTemTeto() {
        LimiteDiarioService servico = servicoDeCadastros(3, 5);
        servico.registrarCadastro("10.0.0.1");
        servico.registrarCadastro("10.0.0.2");
        servico.registrarCadastro("10.0.0.3");

        assertRecusadoComo(
                () -> servico.registrarCadastro("10.0.0.4"),
                LimiteDiarioExcedidoException.Tipo.TOTAL,
                LimiteDiarioExcedidoException.Assunto.CADASTROS);
        assertThat(repositorio.chavesDoDia(DIA_21))
                .filteredOn(chave -> chave.startsWith("cadastro-ip:"))
                .as("o 4º IP, recusado pelo total, não ganhou contador")
                .hasSize(3);
    }

    @Test
    void oContadorDeCadastrosEOutroQueOdePerguntas() {
        LimiteDiarioService servico = servico(100, 10, 10, 50, 1);
        servico.registrarPergunta("203.0.113.5");

        assertThatCode(() -> servico.registrarCadastro("203.0.113.5")).doesNotThrowAnyException();
        assertRecusadoComo(
                () -> servico.registrarCadastro("203.0.113.5"),
                LimiteDiarioExcedidoException.Tipo.IP,
                LimiteDiarioExcedidoException.Assunto.CADASTROS);
        assertThatCode(() -> servico.registrarPergunta("203.0.113.5")).doesNotThrowAnyException();
    }

    @Test
    void cadastroNaoDisparaALimpezaDosContadoresDePerguntas() {
        servicoDeCadastros(50, 5).registrarCadastro("203.0.113.5");

        assertThat(repositorio.limpezasDeIp).isEmpty();
    }

    @Test
    void cadastroDeUmDiaNovoTemContadorNovo() {
        LimiteDiarioService servico = servicoDeCadastros(50, 1);
        servico.registrarCadastro("203.0.113.5");
        assertThatThrownBy(() -> servico.registrarCadastro("203.0.113.5")).isInstanceOf(LimiteDiarioExcedidoException.class);

        relogio.definir(MEIO_DIA_DE_21_DE_OUTUBRO.plusSeconds(24 * 60 * 60));

        assertThatCode(() -> servico.registrarCadastro("203.0.113.5")).doesNotThrowAnyException();
    }

    @Test
    void limiteDeCadastroZeroBloqueiaTodosOsCadastros() {
        assertThatThrownBy(() -> servicoDeCadastros(0, 5).registrarCadastro("203.0.113.5"))
                .isInstanceOf(LimiteDiarioExcedidoException.class);
        assertThatThrownBy(() -> servicoDeCadastros(50, 0).registrarCadastro("203.0.113.5"))
                .isInstanceOf(LimiteDiarioExcedidoException.class);
    }

    @Test
    void oIpDoCadastroNaoVaiEmTextoParaOBanco() {
        servicoDeCadastros(50, 5).registrarCadastro("203.0.113.5");

        assertThat(repositorio.chavesDoDia(DIA_21))
                .containsExactlyInAnyOrder("cadastro-total", CHAVE_DE_CADASTRO_DO_IP_5)
                .allSatisfy(chave -> assertThat(chave).hasSizeLessThanOrEqualTo(80).doesNotContain("203.0.113.5"));
    }

    // ---- Tentativas de login erradas, por IP e por hora -----------------------------------

    // SHA-256 de '203.0.113.5' (calculado fora do código, como o de cima), na hora 12.
    private static final String CHAVE_DE_LOGIN_DO_IP_5_NA_HORA_12 = "login-ip:" + SHA256_DE_203_0_113_5 + ":12";

    @Test
    void aDecimaPrimeiraTentativaErradaNaMesmaHoraEhRecusada() {
        LimiteDiarioService servico = servico(100, 10, 10);
        for (int i = 0; i < 10; i++) {
            servico.reservarTentativaDeLogin("203.0.113.5");
        }

        assertThatThrownBy(() -> servico.reservarTentativaDeLogin("203.0.113.5"))
                .isInstanceOfSatisfying(LimiteDiarioExcedidoException.class, e -> {
                    assertThat(e.assunto()).isEqualTo(LimiteDiarioExcedidoException.Assunto.LOGIN);
                    assertThat(e.tipo()).isEqualTo(LimiteDiarioExcedidoException.Tipo.IP);
                });
        assertThat(repositorio.contagem(DIA_21, CHAVE_DE_LOGIN_DO_IP_5_NA_HORA_12)).as("a recusada não contou").isEqualTo(10);
    }

    @Test
    void outroIpNaoEhAfetadoPeloLimiteDeLoginDeUmIp() {
        LimiteDiarioService servico = servico(100, 10, 2);
        servico.reservarTentativaDeLogin("203.0.113.5");
        servico.reservarTentativaDeLogin("203.0.113.5");

        assertThatCode(() -> servico.reservarTentativaDeLogin("198.51.100.7")).doesNotThrowAnyException();
    }

    @Test
    void aVagaVoltaQuandoAHoraVira() {
        LimiteDiarioService servico = servico(100, 10, 1);
        servico.reservarTentativaDeLogin("203.0.113.5");
        assertThatThrownBy(() -> servico.reservarTentativaDeLogin("203.0.113.5")).isInstanceOf(LimiteDiarioExcedidoException.class);

        // 13:00 em São Paulo (16:00 UTC): outra hora, outra chave.
        relogio.definir(Instant.parse("2026-10-21T16:00:00Z"));

        assertThatCode(() -> servico.reservarTentativaDeLogin("203.0.113.5")).doesNotThrowAnyException();
        assertThat(repositorio.chavesDoDia(DIA_21))
                .containsExactlyInAnyOrder(CHAVE_DE_LOGIN_DO_IP_5_NA_HORA_12, "login-ip:" + SHA256_DE_203_0_113_5 + ":13");
    }

    @Test
    void aVagaDeUmLoginCertoVoltaEOutroErradoAindaCabe() {
        LimiteDiarioService servico = servico(100, 10, 1);

        LimiteDiarioService.TentativaDeLogin vaga = servico.reservarTentativaDeLogin("203.0.113.5");
        servico.devolverTentativaDeLogin(vaga);

        assertThat(repositorio.contagem(DIA_21, CHAVE_DE_LOGIN_DO_IP_5_NA_HORA_12)).isZero();
        assertThatCode(() -> servico.reservarTentativaDeLogin("203.0.113.5")).doesNotThrowAnyException();
    }

    @Test
    void aVagaDevolvidaNaoDeixaAContagemNegativa() {
        LimiteDiarioService servico = servico(100, 10, 5);
        LimiteDiarioService.TentativaDeLogin vaga = servico.reservarTentativaDeLogin("203.0.113.5");

        servico.devolverTentativaDeLogin(vaga);
        servico.devolverTentativaDeLogin(vaga);

        assertThat(repositorio.contagem(DIA_21, CHAVE_DE_LOGIN_DO_IP_5_NA_HORA_12)).isZero();
    }

    @Test
    void limiteDeLoginZeroBloqueiaTodasAsTentativas() {
        assertThatThrownBy(() -> servico(100, 10, 0).reservarTentativaDeLogin("203.0.113.5"))
                .isInstanceOf(LimiteDiarioExcedidoException.class);
    }

    @Test
    void ahoraDaChaveEhADoFusoEDiaTambem() {
        // 23:30 de 20/10 em São Paulo, já 21/10 em UTC: a vaga é de 20/10, hora 23.
        relogio.definir(Instant.parse("2026-10-21T02:30:00Z"));

        servico(100, 10, 10).reservarTentativaDeLogin("203.0.113.5");

        assertThat(repositorio.contagem(LocalDate.of(2026, 10, 20), "login-ip:" + SHA256_DE_203_0_113_5 + ":23")).isEqualTo(1);
    }

    @Test
    void oIpDoLoginNaoVaiEmTextoParaOBanco() {
        servico(100, 10, 10).reservarTentativaDeLogin("203.0.113.5");

        assertThat(repositorio.chavesDoDia(DIA_21)).noneMatch(chave -> chave.contains("203.0.113.5"));
    }

    /** Mesma semântica do SQL de {@link LimiteDiarioRepository}: limite 0 recusa até a criação, e a contagem não passa do limite. */
    private static final class RepositorioEmMemoria extends LimiteDiarioRepository {

        private Map<String, Integer> contagens = new LinkedHashMap<>();
        final List<LocalDate> limpezasDeIp = new ArrayList<>();
        final List<LocalDate> limpezasGerais = new ArrayList<>();
        boolean falharNaLimpeza;

        RepositorioEmMemoria() {
            super(null);
        }

        @Override
        public OptionalInt incrementarSeAbaixoDoLimite(LocalDate dia, String chave, int limite) {
            if (limite < 1) {
                return OptionalInt.empty();
            }
            int atual = contagens.getOrDefault(dia + "|" + chave, 0);
            if (atual >= limite) {
                return OptionalInt.empty();
            }
            contagens.put(dia + "|" + chave, atual + 1);
            return OptionalInt.of(atual + 1);
        }

        @Override
        public void decrementar(LocalDate dia, String chave) {
            contagens.computeIfPresent(dia + "|" + chave, (k, atual) -> Math.max(0, atual - 1));
        }

        @Override
        public int apagarContagensDeIpAnterioresA(LocalDate dia) {
            if (falharNaLimpeza) {
                throw new IllegalStateException("falha simulada na limpeza");
            }
            limpezasDeIp.add(dia);
            return 0;
        }

        @Override
        public int apagarContagensAnterioresA(LocalDate dia) {
            limpezasGerais.add(dia);
            return 0;
        }

        int contagem(LocalDate dia, String chave) {
            return contagens.getOrDefault(dia + "|" + chave, 0);
        }

        List<String> chavesDoDia(LocalDate dia) {
            return contagens.keySet().stream()
                    .filter(k -> k.startsWith(dia + "|"))
                    .map(k -> k.substring((dia + "|").length()))
                    .toList();
        }

        Map<String, Integer> copiar() {
            return new LinkedHashMap<>(contagens);
        }

        void restaurar(Map<String, Integer> copia) {
            contagens = new LinkedHashMap<>(copia);
        }
    }

    /** Transação de mentira: se o corpo lança, devolve o repositório ao estado de antes, como um rollback. */
    private static final class TransacaoDeTeste implements TransactionOperations {

        private final RepositorioEmMemoria repositorio;
        private int desfeitas;

        TransacaoDeTeste(RepositorioEmMemoria repositorio) {
            this.repositorio = repositorio;
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            Map<String, Integer> antes = repositorio.copiar();
            try {
                return action.doInTransaction(new SimpleTransactionStatus());
            } catch (RuntimeException e) {
                repositorio.restaurar(antes);
                desfeitas++;
                throw e;
            }
        }

        int desfeitas() {
            return desfeitas;
        }
    }
}
