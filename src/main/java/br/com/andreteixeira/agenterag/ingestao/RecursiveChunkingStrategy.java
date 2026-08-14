package br.com.andreteixeira.agenterag.ingestao;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.util.List;

/**
 * Estratégia "recursiva pura" — linha de base para comparação (Etapa 4.3 do
 * plano): {@link DocumentSplitters#recursive} direto no documento inteiro,
 * sem dividir por cabeçalho antes. Todo chunk sai com {@code secao = null},
 * porque não há divisão por seção nesta estratégia. NÃO é a estratégia
 * padrão — ver {@code app.ingestao.chunking.strategy}, que continua
 * {@code hybrid}.
 */
public class RecursiveChunkingStrategy implements ChunkingStrategy {

    private final DocumentSplitter splitter;

    public RecursiveChunkingStrategy(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
        this.splitter = DocumentSplitters.recursive(maxSegmentSizeInChars, maxOverlapSizeInChars);
    }

    @Override
    public List<Chunk> split(String documentoCompleto) {
        if (documentoCompleto == null || documentoCompleto.isBlank()) {
            return List.of();
        }
        return splitter.split(Document.from(documentoCompleto)).stream()
                .map(segment -> new Chunk(segment.text(), null))
                .toList();
    }
}
