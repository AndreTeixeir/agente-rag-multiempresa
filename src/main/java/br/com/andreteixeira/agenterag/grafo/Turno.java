package br.com.andreteixeira.agenterag.grafo;

import java.io.Serializable;

/**
 * Um par pergunta/resposta já concluído, guardado no histórico da conversa.
 * {@link Serializable} porque o {@code ObjectStreamStateSerializer} do
 * LangGraph4j cai para serialização Java padrão para tipos que não conhece
 * (String, List, Map e Set têm serializador próprio — um record customizado
 * como este, não).
 */
public record Turno(String pergunta, String resposta) implements Serializable {
}
