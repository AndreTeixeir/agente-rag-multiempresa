package br.com.andreteixeira.agenterag.conta;

import br.com.andreteixeira.agenterag.grafo.ConversaRepository;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioRepository;
import br.com.andreteixeira.agenterag.limite.LimitesProperties;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * O reset diário da demo, à meia-noite do fuso de {@code app.demo.limites.fuso} — o mesmo dos
 * limites. Apaga:
 * <ul>
 *     <li>toda conta que não é semeada, não é o superadmin real e não foi criada por ele
 *     ({@link ContaRepository#apagarContasDeDemonstracao});</li>
 *     <li>TODAS as conversas ({@link ConversaRepository#apagarTodas}), com os checkpoints.</li>
 * </ul>
 * e registra uma linha {@code INFO} com as contagens. As contas semeadas, o superadmin real e as
 * contas que ele criou ficam.
 * <p>
 * <b>Roda uma vez por dia, mesmo com o app fora do ar à meia-noite:</b> cada execução começa
 * reivindicando o dia (linha {@code reset-diario} em {@code limite_diario}, inserida de forma
 * atômica). Quem consegue a reivindicação roda o reset; as demais chamadas do mesmo dia — o boot de
 * depois, uma segunda instância — saem sem fazer nada. Por isso o reset também roda no primeiro boot
 * do dia, que é quando o agendamento da meia-noite pode ter sido perdido. A reivindicação e o apagar
 * são uma transação só: se o apagar falhar, a reivindicação some junto e a próxima tentativa roda.
 * <p>
 * O {@code PostgresSaver} não guarda nada das conversas em memória, então apagar as linhas basta: a
 * próxima mensagem de uma conversa apagada começa uma conversa nova, sem erro (ver
 * {@link ConversaRepository}). Uma pergunta em andamento durante o reset recria a thread dela, o
 * que é inofensivo.
 */
@Service
@Order(2)
public class ResetDiarioService implements ApplicationRunner {

    static final String CHAVE_DO_RESET = "reset-diario";

    private static final Logger log = LoggerFactory.getLogger(ResetDiarioService.class);

    private final LimiteDiarioRepository limites;
    private final ContaRepository contas;
    private final ConversaRepository conversas;
    private final TransactionOperations transacao;
    private final LimitesProperties propriedades;
    private final Clock relogio;

    public ResetDiarioService(
            LimiteDiarioRepository limites,
            ContaRepository contas,
            ConversaRepository conversas,
            TransactionOperations transacao,
            LimitesProperties propriedades,
            Clock relogio) {
        this.limites = limites;
        this.contas = contas;
        this.conversas = conversas;
        this.transacao = transacao;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    /** O primeiro boot do dia roda o reset se o agendamento da meia-noite não rodou (a reivindicação decide). */
    @Override
    public void run(ApplicationArguments args) {
        executarSeNovoDia();
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "${app.demo.limites.fuso}")
    public void resetAgendado() {
        executarSeNovoDia();
    }

    private record Apagado(int contas, int conversas) {
    }

    /**
     * Roda o reset se ele ainda não rodou hoje. Devolve se rodou. Uma falha vira {@code ERROR} no log
     * e nunca derruba o boot nem o agendador; a próxima tentativa roda de novo.
     */
    public boolean executarSeNovoDia() {
        LocalDate hoje = LocalDate.now(relogio.withZone(propriedades.fuso()));
        try {
            Apagado apagado = transacao.execute(status -> {
                if (!limites.reivindicar(hoje, CHAVE_DO_RESET)) {
                    return null;
                }
                return new Apagado(contas.apagarContasDeDemonstracao(), conversas.apagarTodas());
            });
            if (apagado == null) {
                log.debug("Reset diário de {} já rodou.", hoje);
                return false;
            }
            log.info("Reset diário de {}: {} contas de demonstração e {} conversas apagadas.",
                    hoje, apagado.contas(), apagado.conversas());
            return true;
        } catch (RuntimeException e) {
            log.error("Falha no reset diário de {}: nada foi apagado; a próxima execução tenta de novo.", hoje, e);
            return false;
        }
    }
}
