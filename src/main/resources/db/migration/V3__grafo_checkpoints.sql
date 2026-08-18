-- Tabelas de checkpoint do LangGraph4j (Etapa 5 — grafo de conversa), no mesmo
-- Postgres do pgvector, como o plano exige.
--
-- Schema copiado EXATAMENTE do que org.bsc.langgraph4j:langgraph4j-postgres-saver
-- 1.9.0-beta2 (PostgresSaver.initTable) criaria sozinho se
-- Builder.createTables(true) fosse chamado — não foi (ver ConversaGraphConfig,
-- .createTables(false) explícito). As tabelas vêm só desta migração Flyway, por
-- decisão do projeto (schema versionado, Etapa 3) — nunca de criação automática
-- da lib. Nomes de tabela/coluna não usam aspas, de propósito: o driver do
-- postgres-saver também gera SQL sem aspas nas mesmas consultas, e o Postgres
-- dobra identificadores sem aspas para minúsculas em ambos os casos — se um dos
-- dois lados usasse aspas, os nomes resultantes divergiriam e as consultas do
-- saver não encontrariam as tabelas.

CREATE TABLE LG4JThread (
    thread_id UUID PRIMARY KEY,
    thread_name VARCHAR(255),
    is_released BOOLEAN DEFAULT FALSE NOT NULL
);

CREATE TABLE LG4JCheckpoint (
    checkpoint_id UUID PRIMARY KEY,
    parent_checkpoint_id UUID,
    thread_id UUID NOT NULL,
    node_id VARCHAR(255),
    next_node_id VARCHAR(255),
    state_data JSONB NOT NULL,
    state_content_type VARCHAR(100) NOT NULL,
    saved_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_thread
        FOREIGN KEY(thread_id)
        REFERENCES LG4JThread(thread_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_lg4jcheckpoint_thread_id ON LG4JCheckpoint(thread_id);
CREATE INDEX idx_lg4jcheckpoint_thread_id_saved_at_desc ON LG4JCheckpoint(thread_id, saved_at DESC);
CREATE UNIQUE INDEX idx_unique_lg4jthread_thread_name_unreleased ON LG4JThread(thread_name) WHERE is_released = FALSE;
