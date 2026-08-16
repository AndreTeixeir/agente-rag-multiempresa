package br.com.andreteixeira.agenterag.ingestao;

import java.util.List;

/**
 * Porta de embeddings — permite trocar o provedor (Gemini via API, ONNX
 * local, etc.) sem tocar no restante da ingestão/recuperação.
 * <p>
 * Os embeddings do Gemini são sensíveis ao "task type": o mesmo texto gera um
 * vetor diferente dependendo se ele é indexado como documento ou usado como
 * consulta. Usar o tipo errado não gera erro nenhum — só degrada a
 * recuperação silenciosamente. Por isso esta porta expõe dois caminhos
 * explícitos em vez de um único {@code embed(texto)} genérico: não é possível
 * chamar o método errado sem querer.
 */
public interface EmbeddingProvider {

    /**
     * Embeda um lote de textos para indexação (lado documento). Usa batching
     * real quando o provedor suportar — ver {@link GeminiEmbeddingProvider}.
     */
    List<float[]> embedDocuments(List<String> textos);

    /**
     * Embeda uma consulta do usuário (lado busca). Sempre um texto por vez —
     * não há lote de consultas simultâneas em uma sessão de chat.
     */
    float[] embedQuery(String texto);

    /**
     * Dimensão dos vetores retornados por ambos os métodos acima.
     */
    int dimension();
}
