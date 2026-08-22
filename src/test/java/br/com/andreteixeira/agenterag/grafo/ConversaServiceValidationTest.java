package br.com.andreteixeira.agenterag.grafo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Validação manual da Etapa 5 — NÃO é um teste exaustivo, é a trava de custo
 * pedida: no máximo 2 conversas de 3 turnos, sem retry. Cobre o critério de
 * conclusão do plano: conversa multi-turno funciona, follow-up recupera
 * corretamente, estado persiste entre requisições (cada turno é uma chamada
 * de {@link ConversaService#responder} independente — sem isso, "persistir
 * entre requisições" não estaria sendo testado de verdade).
 * <p>
 * Conversa 2, turno 3, tenta trocar de empresa no meio da conversa
 * (empresaEscolhida="bimbam" numa conversa fixada em "santo-pegasus") — é o
 * teste da regra de produto "empresa fixa até o fim da sessão; trocar exige
 * nova conversa".
 * <p>
 * {@code @Tag("llm")}: chamadas reais e pagas (embedding + chat, em cada
 * turno). Rodar explicitamente com:
 * <pre>{@code mvn test -Dtest=ConversaServiceValidationTest -Dgroups=llm -Dsurefire.excludedGroups=}</pre>
 * Sem retry: se qualquer chamada falhar, o método para e o teste falha.
 */
@Tag("llm")
@SpringBootTest
class ConversaServiceValidationTest {

    private static final Logger log = LoggerFactory.getLogger(ConversaServiceValidationTest.class);

    @Autowired
    private ConversaService conversaService;

    /** Ver {@link #pausarSeNaoForOPrimeiro()}. */
    private static final long PAUSA_MS = Long.getLong("pausa.conversa.ms", 26_000L);

    private int chamadas = 0;

    @Test
    void validaDuasConversasDeTresTurnos() {
        conversa1MercadoCentral();
        conversa2SantoPegasus();
    }

    private void conversa1MercadoCentral() {
        String threadId = UUID.randomUUID().toString();
        log.info("========== CONVERSA 1 (mercado-central) — threadId={} ==========", threadId);

        var t1 = turno(threadId, "mercado-central", "Qual a temperatura ideal para produtos congelados?");
        assertThat(t1.empresa()).isEqualTo("mercado-central");
        assertThat(t1.texto()).isNotBlank();

        var t2 = turno(threadId, null, "E para hortifrúti?");
        assertThat(t2.empresa()).isEqualTo("mercado-central");
        assertThat(t2.texto()).isNotBlank();

        var t3 = turno(threadId, null, "E qual o prazo de pagamento a fornecedores da classe A de perecíveis?");
        assertThat(t3.empresa()).isEqualTo("mercado-central");
        assertThat(t3.texto()).isNotBlank();
    }

    private void conversa2SantoPegasus() {
        String threadId = UUID.randomUUID().toString();
        log.info("========== CONVERSA 2 (santo-pegasus) — threadId={} ==========", threadId);

        var t1 = turno(threadId, "santo-pegasus", "Quantos dias de férias um colaborador CLT tem direito?");
        assertThat(t1.empresa()).isEqualTo("santo-pegasus");
        assertThat(t1.texto()).isNotBlank();

        var t2 = turno(threadId, null, "E qual o período mínimo de descanso após ser acionado de madrugada?");
        assertThat(t2.empresa()).isEqualTo("santo-pegasus");
        assertThat(t2.texto()).isNotBlank();

        log.info("--- turno 3: tentando trocar para 'bimbam' no meio da conversa (deve ser ignorado) ---");
        var t3 = turno(threadId, "bimbam", "Qual o CNPJ da empresa?");
        assertThat(t3.empresa())
                .as("empresa deve continuar 'santo-pegasus' — a troca no meio da conversa deve ser ignorada")
                .isEqualTo("santo-pegasus");
        assertThat(t3.texto()).isNotBlank();
    }

    private ConversaService.RespostaConversa turno(String threadId, String empresaEscolhida, String pergunta) {
        pausarSeNaoForOPrimeiro();
        log.info("--- turno: empresaEscolhida={} | pergunta=\"{}\" ---", empresaEscolhida, pergunta);
        var resposta = conversaService.responder(threadId, empresaEscolhida, pergunta);
        log.info("empresa (estado)={}", resposta.empresa());
        log.info("RESPOSTA:\n{}", resposta.texto());
        log.info("FONTES: {}", resposta.fontes());
        return resposta;
    }

    /**
     * Pausa entre turnos para acomodar o rate limit do free tier do Gemini
     * (5 requisições/minuto) — não é espera arbitrária. Cada turno com
     * histórico consome duas requisições (reescrita de consulta + resposta,
     * ver {@code ConversaNodes:131}), o dobro de uma chamada simples — daí o
     * default de 26s, o dobro do usado em {@code RespostaServiceValidationTest}.
     * Sem a pausa, a sexta chamada (por volta do turno 3 da segunda conversa)
     * estoura o limite e o teste falha no meio, sem retry, queimando cota
     * sem produzir resultado.
     * <p>
     * Pulada no primeiro turno, aplicada antes de cada turno seguinte.
     * Configurável via {@code -Dpausa.conversa.ms} (default 26000 = 26s).
     */
    private void pausarSeNaoForOPrimeiro() {
        if (chamadas++ == 0) {
            return;
        }
        try {
            Thread.sleep(PAUSA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pausa entre turnos interrompida", e);
        }
    }
}
