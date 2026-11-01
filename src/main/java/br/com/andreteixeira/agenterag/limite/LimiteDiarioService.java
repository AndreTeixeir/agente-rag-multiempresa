package br.com.andreteixeira.agenterag.limite;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Limites diários do chat público: um total para a demo inteira e um por IP.
 * <p>
 * <b>Conta a pergunta aceita</b>, não a chamada ao modelo (uma pergunta faz 1
 * ou 2 chamadas, conforme haja histórico) — {@link #registrarPergunta} roda
 * ANTES de o grafo ser chamado. Pergunta recusada por um limite não conta em
 * nenhum contador: o total e o IP são incrementados na mesma transação, e a
 * recusa pelo IP lança a exceção de dentro dela, o que desfaz o incremento do
 * total. A ordem é sempre a mesma (total, depois IP), o que evita deadlock
 * entre requisições simultâneas, e a transação fecha antes de o grafo rodar —
 * nenhuma linha fica travada durante a chamada ao modelo.
 * <p>
 * <b>Privacidade:</b> o IP nunca é gravado em texto, só o hash SHA-256 dele
 * (sem sal — ver o relatório da rodada). Contagens de IP de dias anteriores
 * (as de perguntas e as de tentativas de login) são apagadas, e só o dia
 * corrente fica guardado; as do total ficam {@value #DIAS_DE_RETENCAO_DO_TOTAL} dias.
 * <p>
 * <b>O dia vira à meia-noite do fuso configurado</b>
 * ({@code app.demo.limites.fuso}), a partir do {@link Clock} injetado.
 */
@Service
public class LimiteDiarioService {

    private static final Logger log = LoggerFactory.getLogger(LimiteDiarioService.class);

    static final String CHAVE_TOTAL = "total";
    static final String PREFIXO_CHAVE_IP = "ip:";
    static final String PREFIXO_CHAVE_LOGIN = "login-ip:";
    static final String CHAVE_CADASTRO_TOTAL = "cadastro-total";
    static final String PREFIXO_CHAVE_CADASTRO_IP = "cadastro-ip:";
    static final int DIAS_DE_RETENCAO_DO_TOTAL = 7;

    private static final String IP_DESCONHECIDO = "desconhecido";

    private final LimiteDiarioRepository repository;
    private final TransactionOperations transacao;
    private final LimitesProperties limites;
    private final Clock relogio;

    public LimiteDiarioService(
            LimiteDiarioRepository repository, TransactionOperations transacao, LimitesProperties limites, Clock relogio) {
        this.repository = repository;
        this.transacao = transacao;
        this.limites = limites;
        this.relogio = relogio;
    }

    /**
     * Registra uma pergunta do {@code ip} nos dois contadores do dia, ou lança
     * {@link LimiteDiarioExcedidoException} sem registrar nada.
     */
    public void registrarPergunta(String ip) {
        LocalDate hoje = hoje();
        int totalDeHoje = consumir(
                hoje,
                CHAVE_TOTAL,
                limites.perguntasPorDia(),
                PREFIXO_CHAVE_IP + sha256Hex(ipOuDesconhecido(ip)),
                limites.perguntasPorIpPorDia(),
                LimiteDiarioExcedidoException.Assunto.PERGUNTAS);

        if (totalDeHoje == 1) {
            // Cobre a aplicação ter ficado fora do ar à meia-noite, quando a limpeza agendada não rodou.
            limparAntigos(hoje);
        }
    }

    /**
     * Registra um cadastro de conta do {@code ip} nos dois contadores do dia (total e por IP), ou lança
     * {@link LimiteDiarioExcedidoException} sem registrar nada — a mesma regra das perguntas, com
     * contadores só dos cadastros. Excluir a conta depois NÃO devolve a vaga. Quem chama de dentro de
     * uma transação a estende: se o cadastro falhar depois, a vaga volta com o rollback.
     */
    public void registrarCadastro(String ip) {
        consumir(
                hoje(),
                CHAVE_CADASTRO_TOTAL,
                limites.cadastrosPorDia(),
                PREFIXO_CHAVE_CADASTRO_IP + sha256Hex(ipOuDesconhecido(ip)),
                limites.cadastrosPorIpPorDia(),
                LimiteDiarioExcedidoException.Assunto.CADASTROS);
    }

    /**
     * Incrementa o contador do total e o do IP na mesma transação, nesta ordem (a ordem fixa evita
     * deadlock entre requisições simultâneas). Recusar pelo IP lança de dentro da transação, o que
     * desfaz o incremento do total. Devolve a contagem do total depois de incrementar.
     */
    private int consumir(
            LocalDate hoje,
            String chaveDoTotal,
            int limiteDoTotal,
            String chaveDoIp,
            int limiteDoIp,
            LimiteDiarioExcedidoException.Assunto assunto) {
        Integer totalDeHoje = transacao.execute(status -> {
            OptionalInt total = repository.incrementarSeAbaixoDoLimite(hoje, chaveDoTotal, limiteDoTotal);
            if (total.isEmpty()) {
                throw new LimiteDiarioExcedidoException(LimiteDiarioExcedidoException.Tipo.TOTAL, assunto);
            }
            OptionalInt doIp = repository.incrementarSeAbaixoDoLimite(hoje, chaveDoIp, limiteDoIp);
            if (doIp.isEmpty()) {
                // Lançar de dentro da transação desfaz o incremento do total acima.
                throw new LimiteDiarioExcedidoException(LimiteDiarioExcedidoException.Tipo.IP, assunto);
            }
            return total.getAsInt();
        });
        return totalDeHoje == null ? 0 : totalDeHoje;
    }

    private static String ipOuDesconhecido(String ip) {
        return ip == null || ip.isBlank() ? IP_DESCONHECIDO : ip;
    }

    /**
     * Reserva, de forma atômica, uma das tentativas de login do {@code ip} na hora atual — ANTES de
     * a senha ser conferida, então nenhuma tentativa em paralelo passa do limite. Sem vaga, lança
     * {@link LimiteDiarioExcedidoException} (429) e a senha nem chega a ser conferida, nem a certa.
     * Quem acerta o login devolve a vaga com {@link #devolverTentativaDeLogin}: só as tentativas
     * erradas consomem o limite.
     * <p>
     * A chave é {@code login-ip:<sha256 do IP>:<hora>}; o dia está na própria linha. A hora é a do
     * relógio no fuso configurado, e a vaga volta quando ela vira.
     */
    public TentativaDeLogin reservarTentativaDeLogin(String ip) {
        LocalDateTime agora = LocalDateTime.now(relogio.withZone(limites.fuso()));
        String chave = PREFIXO_CHAVE_LOGIN
                + sha256Hex(ipOuDesconhecido(ip))
                + ":"
                + String.format("%02d", agora.getHour());
        LocalDate dia = agora.toLocalDate();
        if (repository.incrementarSeAbaixoDoLimite(dia, chave, limites.tentativasDeLoginPorIpPorHora()).isEmpty()) {
            throw new LimiteDiarioExcedidoException(LimiteDiarioExcedidoException.Tipo.IP, LimiteDiarioExcedidoException.Assunto.LOGIN);
        }
        return new TentativaDeLogin(dia, chave);
    }

    /** Devolve a vaga de um login que deu certo. */
    public void devolverTentativaDeLogin(TentativaDeLogin tentativa) {
        repository.decrementar(tentativa.dia(), tentativa.chave());
    }

    /** A vaga reservada por {@link #reservarTentativaDeLogin}: a linha de {@code limite_diario} que a guarda. */
    public record TentativaDeLogin(LocalDate dia, String chave) {
    }

    /**
     * Limpeza agendada, um minuto depois da meia-noite do fuso configurado: os
     * hashes de IP de ontem não ficam esperando a primeira pergunta do dia.
     */
    @Scheduled(cron = "0 1 0 * * *", zone = "${app.demo.limites.fuso}")
    public void limparAntigosAgendado() {
        limparAntigos(hoje());
    }

    /**
     * Falha na limpeza nunca derruba a pergunta que a disparou: vira aviso no
     * log, e a próxima limpeza tenta de novo.
     */
    private void limparAntigos(LocalDate hoje) {
        try {
            int ips = repository.apagarContagensDeIpAnterioresA(hoje);
            int antigos = repository.apagarContagensAnterioresA(hoje.minusDays(DIAS_DE_RETENCAO_DO_TOTAL));
            log.info("Limpeza dos contadores diários: {} contagens com hash de IP de dias anteriores e {} com mais de {} dias apagadas",
                    ips, antigos, DIAS_DE_RETENCAO_DO_TOTAL);
        } catch (RuntimeException e) {
            log.warn("Não foi possível limpar os contadores diários antigos", e);
        }
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(limites.fuso()));
    }

    private static String sha256Hex(String texto) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 é de implementação obrigatória em toda JVM.
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
