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
     * Embeda um lote de consultas de uma vez, com o mesmo task type de
     * {@link #embedQuery} (RETRIEVAL_QUERY) — não confundir com
     * {@link #embedDocuments}, que usa RETRIEVAL_DOCUMENT.
     * <p>
     * Existe só para o harness de avaliação (Etapa 4 do plano), que precisa
     * embedar dezenas de perguntas do conjunto de teste sem gastar uma
     * chamada HTTP por pergunta. O chat real nunca usa este método — uma
     * sessão sempre tem uma pergunta de cada vez, e é para isso que
     * {@link #embedQuery} continua existindo e continua sendo o caminho
     * único de produção.
     */
    List<float[]> embedQueries(List<String> textos);

    /**
     * Dimensão dos vetores retornados por ambos os métodos acima.
     */
    int dimension();
}
