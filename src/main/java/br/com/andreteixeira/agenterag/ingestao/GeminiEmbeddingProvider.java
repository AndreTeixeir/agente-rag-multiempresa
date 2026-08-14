package br.com.andreteixeira.agenterag.ingestao;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.request.EmbeddingInputType;
import dev.langchain4j.model.embedding.request.EmbeddingRequest;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import dev.langchain4j.model.googleai.GoogleAiEmbeddingModel;
import java.util.List;

/**
 * {@link EmbeddingProvider} via API do Google Gemini ({@code gemini-embedding-001}).
 * <p>
 * <b>Task type:</b> o bean {@link GoogleAiEmbeddingModel} é configurado (em
 * {@link IngestionConfig}) com {@code taskType = RETRIEVAL_DOCUMENT} — esse é
 * o valor que {@link GoogleAiEmbeddingModel#embedAll} usa (não aceita
 * override por chamada; confirmado lendo o bytecode da versão 1.18.1: o
 * método lê o campo {@code taskType} do próprio builder, sem olhar para
 * nenhum parâmetro de tipo por requisição). Por isso {@link #embedDocuments}
 * pode usar {@code embedAll} diretamente. Já {@link #embedQuery} não pode
 * usar o mesmo caminho — precisa de {@code RETRIEVAL_QUERY} — então monta um
 * {@link EmbeddingRequest} explícito com {@link EmbeddingInputType#QUERY},
 * que o modelo lê por chamada e sobrepõe ao {@code taskType} do builder
 * (também confirmado no bytecode: {@code toTaskType} só cai no valor do
 * builder quando o {@code EmbeddingInputType} da requisição é nulo).
 * <p>
 * <b>Normalização:</b> com {@code outputDimensionality} truncado abaixo de
 * 3072, a API do Gemini não devolve o vetor normalizado em L2 (confirmado na
 * chamada de teste do gate — norma bruta de 0,589), e a
 * {@link GoogleAiEmbeddingModel} da LangChain4j também não normaliza. Os dois
 * caminhos normalizam aqui antes de devolver o vetor.
 */
public class GeminiEmbeddingProvider implements EmbeddingProvider {

    private final GoogleAiEmbeddingModel model;
    private final int dimension;

    public GeminiEmbeddingProvider(GoogleAiEmbeddingModel model, int dimension) {
        this.model = model;
        this.dimension = dimension;
    }

    @Override
    public List<float[]> embedDocuments(List<String> textos) {
        if (textos.isEmpty()) {
            return List.of();
        }
        List<TextSegment> segmentos = textos.stream().map(TextSegment::from).toList();
        List<Embedding> embeddings = model.embedAll(segmentos).content();
        return embeddings.stream().map(e -> normalizeL2(e.vector())).toList();
    }

    @Override
    public float[] embedQuery(String texto) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .input(texto)
                .inputType(EmbeddingInputType.QUERY)
                .build();
        EmbeddingResponse response = model.embed(request);
        return normalizeL2(response.embeddings().get(0).vector());
    }

    @Override
    public int dimension() {
        return dimension;
    }

    private static float[] normalizeL2(float[] vector) {
        double sumSquares = 0.0;
        for (float v : vector) {
            sumSquares += (double) v * v;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm == 0.0) {
            return vector;
        }
        float[] normalized = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            normalized[i] = (float) (vector[i] / norm);
        }
        return normalized;
    }
}
