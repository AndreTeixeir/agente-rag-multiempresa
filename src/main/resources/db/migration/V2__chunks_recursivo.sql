-- Segunda tabela de chunks, para a Etapa 4.3 (decisão de chunking por medição):
-- mesma estrutura exata de V1__chunks_iniciais.sql, mas alimentada pela estratégia
-- "recursive" (linha de base de comparação), em vez de "hybrid". Tabela separada,
-- não uma coluna a mais na "document_chunks" — a tabela híbrida (719 chunks) nunca
-- é tocada por esta migração nem pela ingestão que grava aqui: são dois corpos de
-- dados independentes, comparados lado a lado pelo harness da Etapa 4.2a apontado
-- para cada um via profile Spring (ver application.yml, profile "recursivo").
--
-- Diferença esperada de conteúdo (não de schema): com chunking "recursive" não há
-- divisão por cabeçalho Markdown antes do chunking de tamanho fixo, então todo
-- chunk grava "secao = NULL" (ver RecursiveChunkingStrategy) — o schema aceita
-- isso desde já (secao é nullable também em V1), não precisa de migração diferente.

CREATE TABLE document_chunks_recursive (
    embedding_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    embedding    vector(768) NOT NULL,
    text         TEXT NOT NULL,
    empresa      VARCHAR(50) NOT NULL,
    documento    VARCHAR(255) NOT NULL,
    secao        VARCHAR(255),
    pagina       INTEGER
);

CREATE INDEX idx_document_chunks_recursive_empresa ON document_chunks_recursive (empresa);

CREATE INDEX idx_document_chunks_recursive_embedding ON document_chunks_recursive
    USING hnsw (embedding vector_cosine_ops);
