package br.com.andreteixeira.agenterag.ingestao;

import java.util.List;

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
     * Busca os chunks mais próximos de {@code queryEmbedding} dentro de uma
     * única empresa — o isolamento entre tenants é este filtro no banco, não
     * confiança no embedding (ver CLAUDE.md). {@code empresa} é obrigatório:
     * não existe sobrecarga sem empresa nem valor default, porque não existe
     * busca sem empresa selecionada no fluxo do agente.
     * <p>
     * {@code queryEmbedding} precisa vir normalizado em L2 (mesma convenção
     * usada na ingestão — ver {@link EmbeddingProvider}), já que o score
     * devolvido é a similaridade de cosseno (1 - distância de cosseno).
     *
     * @param empresa        filtro obrigatório de tenant
     * @param queryEmbedding vetor da consulta, já normalizado em L2
     * @param limit          número máximo de resultados
     * @param threshold      score mínimo de similaridade de cosseno para um
     *                       chunk entrar no resultado (não é um limiar de
     *                       produto — quem decide isso é quem chama; a Etapa
     *                       4.2a passa {@code 0.0} para não filtrar nada e só
     *                       observar a distribuição real de scores)
     * @return resultados ordenados por score decrescente, no máximo
     *         {@code limit} itens
     */
    List<SearchResult> search(String empresa, float[] queryEmbedding, int limit, double threshold);

    /**
     * Um resultado de busca: o texto do chunk, os metadados que permitem
     * citar a fonte (documento + seção), e o score de similaridade de
     * cosseno com a consulta.
     */
    record SearchResult(String texto, String documento, String secao, double score) {
    }

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
