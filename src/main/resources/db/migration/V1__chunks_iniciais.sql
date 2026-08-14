-- Schema inicial de ingestão: chunks de documentos com embedding e metadados.
--
-- Decisão de schema (Etapa 3): tabela única "document_chunks" com coluna "empresa"
-- indexada, em vez de schema/tabela por empresa. Justificativa: só 3 tenants fixos e
-- conhecidos (bimbam, mercado-central, santo-pegasus), sem previsão de crescimento
-- multi-tenant dinâmico, volume total de 14 documentos -> algumas centenas de chunks.
-- Schema/tabela por tenant multiplicaria migrações e conexões sem ganho real nesta
-- escala, e dificultaria consultas administrativas que cruzem empresas (ex.: contagem
-- de chunks por empresa no log de ingestão, Etapa 3). O isolamento de tenant não
-- depende do embedding nem de filtro em memória: toda consulta de recuperação filtra
-- "WHERE empresa = ?" no banco, com índice B-tree dedicado.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunks (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding    vector(768) NOT NULL,
    text         TEXT NOT NULL,
    empresa      VARCHAR(50) NOT NULL,
    documento    VARCHAR(255) NOT NULL,
    secao        VARCHAR(255),
    pagina       INTEGER
);

-- Filtro de tenant: toda busca de retrieval restringe por empresa antes de
-- comparar embeddings — este índice é o que torna esse filtro barato.
CREATE INDEX idx_document_chunks_empresa ON document_chunks (empresa);

-- Busca vetorial aproximada por similaridade de cosseno (mesma métrica usada pela
-- query do PgVectorEmbeddingStore: operador "<=>"). HNSW em vez de IVFFlat porque
-- não depende de a tabela já estar populada para construir um índice eficiente, e é
-- a recomendação atual do próprio pgvector para a maioria dos casos de uso.
CREATE INDEX idx_document_chunks_embedding ON document_chunks
    USING hnsw (embedding vector_cosine_ops);
