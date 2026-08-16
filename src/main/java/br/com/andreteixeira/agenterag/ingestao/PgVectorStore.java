package br.com.andreteixeira.agenterag.ingestao;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import java.sql.SQLException;
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
