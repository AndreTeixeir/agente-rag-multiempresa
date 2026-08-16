package br.com.andreteixeira.agenterag.ingestao;

/**
 * Porta de armazenamento vetorial — permite trocar pgvector por uma
 * implementação em memória (testes) sem tocar no restante da ingestão.
 * <p>
 * Idempotência é por documento, não pela coleção inteira: uma falha na
 * ingestão não obriga a recomeçar do zero — ver {@link IngestionService}.
 */
public interface VectorStore {

    /**
     * Grava um chunk (embedding + texto + metadados). O id é gerado
     * internamente.
     */
    void add(float[] embedding, String text, ChunkMetadata metadata);

    /**
     * Total de chunks armazenados de todas as empresas — só para log-resumo.
     */
    long count();

    /**
     * Total de chunks armazenados de uma empresa — só para log-resumo.
     */
    long countByEmpresa(String empresa);

    /**
     * Total de chunks já armazenados de um documento específico — é a guarda
     * de idempotência real: {@code > 0} e sem {@code --force} pula o
     * documento.
     */
    long countByDocumento(String documento);

    /**
     * Apaga só os chunks de um documento — usado por {@code --force} para
     * reingerir um documento sem tocar nos demais.
     */
    void deleteByDocumento(String documento);
}
