package br.com.andreteixeira.agenterag.ingestao;

import com.pgvector.PGvector;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

/**
 * {@link VectorStore} via pgvector. A tabela, as colunas de metadado e os
 * índices (busca vetorial + filtro de empresa) já existem antes de a aplicação
 * subir — criados pela migração Flyway ({@code V1__chunks_iniciais.sql}), não
 * por esta classe.
 */
public class PgVectorStore implements VectorStore {

    private final PgVectorEmbeddingStore store;
    private final DataSource dataSource;
    private final String table;

    public PgVectorStore(PgVectorEmbeddingStore store, DataSource dataSource, String table) {
        this.store = store;
        this.dataSource = dataSource;
        this.table = table;
    }

    @Override
    public void add(float[] embedding, String text, ChunkMetadata metadata) {
        Metadata md = new Metadata()
                .put("empresa", metadata.empresa())
                .put("documento", metadata.documento());
        if (metadata.secao() != null) {
            md.put("secao", metadata.secao());
        }
        if (metadata.pagina() != null) {
            md.put("pagina", metadata.pagina());
        }
        store.add(Embedding.from(embedding), TextSegment.from(text, md));
    }

    @Override
    public long count() {
        return countWhere(null, null);
    }

    @Override
    public long countByEmpresa(String empresa) {
        return countWhere("empresa", empresa);
    }

    @Override
    public long countByDocumento(String documento) {
        return countWhere("documento", documento);
    }

    /**
     * {@code score} é a similaridade de cosseno ({@code 1 - distância}) — o
     * operador {@code <=>} do pgvector devolve distância, não similaridade.
     * A expressão de score aparece três vezes na consulta (SELECT, filtro de
     * limiar e ORDER BY) de propósito: manter o {@code ORDER BY} na expressão
     * bruta sobre a coluna indexada é o que deixa a consulta elegível para o
     * índice HNSW quando o volume crescer (ver relatório da Etapa 3 — no
     * volume atual de 719 chunks o otimizador prefere sequential scan, e isso
     * é esperado, não defeito).
     */
    @Override
    public List<SearchResult> search(String empresa, float[] queryEmbedding, int limit, double threshold) {
        String sql = "SELECT text, documento, secao, 1 - (embedding <=> ?) AS score "
                + "FROM " + table + " "
                + "WHERE empresa = ? AND 1 - (embedding <=> ?) >= ? "
                + "ORDER BY embedding <=> ? "
                + "LIMIT ?";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            PGvector vetor = new PGvector(queryEmbedding);
            statement.setObject(1, vetor);
            statement.setString(2, empresa);
            statement.setObject(3, vetor);
            statement.setDouble(4, threshold);
            statement.setObject(5, vetor);
            statement.setInt(6, limit);
            try (var resultSet = statement.executeQuery()) {
                List<SearchResult> resultados = new ArrayList<>();
                while (resultSet.next()) {
                    resultados.add(new SearchResult(
                            resultSet.getString("text"),
                            resultSet.getString("documento"),
                            resultSet.getString("secao"),
                            resultSet.getDouble("score")));
                }
                return resultados;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao buscar chunks da empresa " + empresa, e);
        }
    }

    @Override
    public void deleteByDocumento(String documento) {
        String sql = "DELETE FROM " + table + " WHERE documento = ?";
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setString(1, documento);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao apagar chunks do documento " + documento, e);
        }
    }

    private long countWhere(String coluna, String valor) {
        String sql = "SELECT COUNT(*) FROM " + table + (coluna != null ? " WHERE " + coluna + " = ?" : "");
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            if (coluna != null) {
                statement.setString(1, valor);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Falha ao contar chunks em " + table, e);
        }
    }
}
