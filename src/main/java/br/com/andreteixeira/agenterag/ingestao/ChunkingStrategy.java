package br.com.andreteixeira.agenterag.ingestao;

import java.util.List;

/**
 * Estratégia de divisão de um documento inteiro em chunks. Trocar a política
 * de chunking (híbrida por seção, recursiva pura, ou qualquer outra) é
 * implementar esta interface e apontar para ela em
 * {@code app.ingestao.chunking.strategy} — não é refatoração do resto da
 * ingestão.
 * <p>
 * A estratégia decide também o que vira metadado {@code secao} de cada chunk
 * — ver {@link Chunk} — porque só ela sabe se e como o texto foi dividido por
 * cabeçalho.
 */
public interface ChunkingStrategy {

    List<Chunk> split(String documentoCompleto);

    record Chunk(String texto, String secao) {
    }
}
