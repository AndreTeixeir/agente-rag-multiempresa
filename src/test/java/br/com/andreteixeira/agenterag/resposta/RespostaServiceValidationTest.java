package br.com.andreteixeira.agenterag.resposta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Validação manual da Etapa 4.2b — NÃO é uma reexecução do conjunto de
 * avaliação inteiro (isso é o harness da Etapa 4.2a, que não é tocado aqui).
 * Só 6 casos, escolhidos a dedo para cobrir os dois caminhos do
 * {@link RespostaService} (comporta 1 e comporta 2) e o requisito de
 * isolamento por empresa — cota de geração do free tier é apertada.
 * <p>
 * {@code @Tag("llm")}: chamadas reais e pagas (embedding + chat). Rodar
 * explicitamente com:
 * <pre>{@code mvn test -Dtest=RespostaServiceValidationTest -Dgroups=llm -Dsurefire.excludedGroups=}</pre>
 * Sem retry: se qualquer chamada falhar, o método para e o teste falha — não
 * há tentativa de continuar os casos restantes.
 */
@Tag("llm")
@SpringBootTest
class RespostaServiceValidationTest {

    private static final Logger log = LoggerFactory.getLogger(RespostaServiceValidationTest.class);

    @Autowired
    private RespostaService respostaService;

    /** Ver {@link #pausarSeNaoForAPrimeira()}. */
    private static final long PAUSA_MS = Long.getLong("pausa.resposta.ms", 13_000L);

    private int chamadas = 0;

    @Test
    void validaSeisCasosContraOModeloReal() {
        // 1-2: fatos precisos que recuperaram na posição 1 na Etapa 4.2a — devem
        // responder com fonte.
        RespostaService.Resposta fato1 = caso(
                "FATO PRECISO 1 (bimbam, pos.1 na 4.2a, score 0.8285)",
                "bimbam",
                "Quais métodos de pagamento a BimBam Buy aceita?");
        assertThat(fato1.texto()).isNotBlank();
        assertThat(fato1.fontes()).isNotEmpty();

        RespostaService.Resposta fato2 = caso(
                "FATO PRECISO 2 (santo-pegasus, pos.1 na 4.2a, score 0.8557)",
                "santo-pegasus",
                "Quantos dias de férias um colaborador CLT da Santo Pegasus tem direito após completar 12 meses?");
        assertThat(fato2.texto()).isNotBlank();
        assertThat(fato2.fontes()).isNotEmpty();

        // 3-4: colisão — mesma pergunta, duas empresas, deve dar respostas diferentes
        // (10 dias vs. 7 dias, Etapa 4.2a).
        RespostaService.Resposta colisaoBimbam =
                caso("COLISÃO — bimbam", "bimbam", "Qual o prazo para desistir da compra por arrependimento?");
        assertThat(colisaoBimbam.texto()).isNotBlank();

        RespostaService.Resposta colisaoMercadoCentral = caso(
                "COLISÃO — mercado-central (mesma pergunta, empresa diferente)",
                "mercado-central",
                "Qual o prazo para desistir da compra por arrependimento?");
        assertThat(colisaoMercadoCentral.texto()).isNotBlank();

        // 5: fora de escopo topicamente estrangeiro — score muito abaixo do limiar
        // (0.5633 na 4.2a < 0.68), comporta 1 deve barrar ANTES do LLM.
        RespostaService.Resposta franca =
                caso("FORA DE ESCOPO ESTRANGEIRO (comporta 1 esperada, SEM chamada ao LLM)", "bimbam", "Qual a capital da França?");
        assertThat(franca.fontes()).as("comporta 1 não deveria recuperar nenhuma fonte").isEmpty();
        assertThat(franca.texto()).contains("Não encontrei");

        // 6: fora de escopo topicamente nativo — score 0.7759 na 4.2a, ACIMA do
        // limiar 0.68, então passa a comporta 1 e chega ao LLM. Este é o caso mais
        // importante: o contexto recuperado é real e topicamente relacionado
        // (métodos de pagamento/logística da BimBam), mas não contém o CNPJ — o LLM
        // precisa admitir ausência, não inventar um número.
        RespostaService.Resposta cnpj = caso(
                "FORA DE ESCOPO NATIVO — CASO MAIS IMPORTANTE (score 0.7759 > limiar 0.68, LLM precisa admitir ausência)",
                "bimbam",
                "Qual o CNPJ da BimBam Buy?");
        assertThat(cnpj.texto()).isNotBlank();
        assertThat(cnpj.fontes()).as("passou a comporta 1, então recuperou fontes reais").isNotEmpty();
    }

    private RespostaService.Resposta caso(String rotulo, String empresa, String pergunta) {
        pausarSeNaoForAPrimeira();
        log.info("=== {} ===", rotulo);
        log.info("empresa={} | pergunta=\"{}\"", empresa, pergunta);
        RespostaService.Resposta resposta = respostaService.responder(empresa, pergunta);
        log.info("RESPOSTA:\n{}", resposta.texto());
        log.info("FONTES: {}", resposta.fontes());
        return resposta;
    }

    /**
     * Pausa entre chamadas para acomodar o rate limit do free tier do Gemini
     * (5 requisições/minuto) — não é espera arbitrária. Sem ela, chamadas em
     * sequência rápida estouram o limite e o teste falha no meio (sem
     * retry), queimando cota sem produzir resultado.
     * <p>
     * Pulada na primeira chamada, aplicada antes de cada chamada seguinte.
     * Configurável via {@code -Dpausa.resposta.ms} (default 13000 = 13s).
     */
    private void pausarSeNaoForAPrimeira() {
        if (chamadas++ == 0) {
            return;
        }
        try {
            Thread.sleep(PAUSA_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pausa entre chamadas interrompida", e);
        }
    }
}
