package br.com.andreteixeira.agenterag.ingestao;

/**
 * Metadados de um chunk. {@code secao} e {@code pagina} podem ser {@code null}
 * quando não é possível determiná-los a partir do Markdown de origem — ver
 * {@link IngestionService} para a extração e {@code docs/DECISOES.md} para o
 * registro da limitação.
 */
public record ChunkMetadata(String empresa, String documento, String secao, Integer pagina) {
}
