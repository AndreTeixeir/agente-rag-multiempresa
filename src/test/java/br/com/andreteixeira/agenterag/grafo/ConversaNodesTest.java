package br.com.andreteixeira.agenterag.grafo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Cobre a validação de {@code empresaEscolhida} em
 * {@link ConversaNodes#identificarEmpresa} (correção do achado #1 da revisão
 * de diff da Etapa 5, {@code 2026-08-18-etapa-5-revisao-diff.md}). Só chama
 * lógica Java pura — sem Spring context, sem API — por isso NÃO tem
 * {@code @Tag("llm")} e roda dentro do {@code mvn test} padrão.
 */
class ConversaNodesTest {

    // respostaService/chatModel não são usados por identificarEmpresa — null é seguro aqui.
    private final ConversaNodes nodes = new ConversaNodes(null, null);

    @Test
    void empresaValidaFixaOEstado() {
        ConversaState state = new ConversaState(Map.of(ConversaState.EMPRESA_ESCOLHIDA, "mercado-central"));

        Map<String, Object> atualizacao = nodes.identificarEmpresa(state);

        assertThat(atualizacao).containsExactly(Map.entry(ConversaState.EMPRESA, "mercado-central"));
    }

    @Test
    void empresaForaDoConjuntoLancaIllegalArgumentException() {
        ConversaState state = new ConversaState(Map.of(ConversaState.EMPRESA_ESCOLHIDA, "BimBam Buy"));

        assertThatThrownBy(() -> nodes.identificarEmpresa(state))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Empresa 'BimBam Buy' não reconhecida. Valores aceitos: [bimbam, mercado-central, santo-pegasus]");
    }

    @Test
    void empresaAusenteContinuaLancandoIllegalStateException() {
        ConversaState state = new ConversaState(Map.of());

        assertThatThrownBy(() -> nodes.identificarEmpresa(state))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Primeira mensagem da conversa precisa informar a empresa — o sistema não adivinha empresa.");
    }

    /** Não pedido explicitamente, mas cobre que a correção não quebrou o caminho já existente. */
    @Test
    void empresaJaFixaIgnoraQualquerNovoValorMesmoInvalido() {
        ConversaState state =
                new ConversaState(Map.of(ConversaState.EMPRESA, "santo-pegasus", ConversaState.EMPRESA_ESCOLHIDA, "valor-invalido"));

        Map<String, Object> atualizacao = nodes.identificarEmpresa(state);

        assertThat(atualizacao).isEmpty();
    }
}
