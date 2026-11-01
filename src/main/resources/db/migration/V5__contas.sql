-- Contas de acesso ao chat (login e papéis).
--
-- Antes desta migração o chat era público. Agora cada pessoa entra com uma conta, e o
-- papel dela decide o que pode fazer: USUARIO e ADMIN conversam só com a empresa da conta
-- (e o ADMIN administra usuários dessa empresa); SUPERADMIN conversa com qualquer empresa.
--
-- Colunas:
--  * usuario          - nome de entrada, único, em minúsculas (formato garantido pelo CHECK).
--  * senha_hash       - hash BCrypt; a senha nunca é guardada.
--  * empresa          - slug da empresa (bimbam, mercado-central, santo-pegasus). Obrigatória
--                       para USUARIO e ADMIN e nula para SUPERADMIN. A lista de slugs fica no
--                       código (Empresas), como no resto do projeto, e não aqui.
--  * semeada          - conta criada por esta migração (as 7 contas de demonstração).
--  * superadmin_real  - o superadmin criado no boot a partir de variáveis de ambiente
--                       (SuperadminReal). No máximo um, garantido pelo índice parcial abaixo.
--  * criada_por       - conta que criou esta; vira NULL se aquela for excluída.
--
-- Conta protegida (as contas de demonstração não alteram nem excluem) é derivada, não
-- guardada: semeada, ou superadmin real, ou criada pelo superadmin real.

CREATE TABLE conta (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario         VARCHAR(40)  NOT NULL,
    senha_hash      VARCHAR(100) NOT NULL,
    papel           VARCHAR(20)  NOT NULL,
    empresa         VARCHAR(50),
    semeada         BOOLEAN      NOT NULL DEFAULT FALSE,
    superadmin_real BOOLEAN      NOT NULL DEFAULT FALSE,
    criada_por      BIGINT       REFERENCES conta (id) ON DELETE SET NULL,
    criada_em       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT conta_usuario_formato CHECK (usuario ~ '^[a-z0-9._-]{3,40}$'),
    CONSTRAINT conta_papel_valido CHECK (papel IN ('USUARIO', 'ADMIN', 'SUPERADMIN')),
    CONSTRAINT conta_empresa_conforme_papel CHECK (
        (papel = 'SUPERADMIN' AND empresa IS NULL) OR (papel <> 'SUPERADMIN' AND empresa IS NOT NULL)),
    CONSTRAINT conta_real_so_superadmin CHECK (NOT superadmin_real OR papel = 'SUPERADMIN')
);

-- O id nunca é reaproveitado (IDENTITY), então uma conta recriada com o mesmo usuário não
-- herda as conversas da anterior: elas são identificadas por <id>:<threadId>.
CREATE UNIQUE INDEX idx_conta_usuario ON conta (usuario);

-- No máximo um superadmin real.
CREATE UNIQUE INDEX idx_conta_superadmin_real ON conta (superadmin_real) WHERE superadmin_real;

-- As 7 contas de demonstração. A senha de todas é pública (sabia-demo) e aparece na tela de
-- login da demo; os hashes são BCrypt (custo 10), gerados fora do banco, um por conta.
INSERT INTO conta (usuario, senha_hash, papel, empresa, semeada) VALUES
    ('superadmin.demo',        '$2a$10$nm3cEiA4oBn8yX2Tn71moeBbU9JvkpSQ8yCxECD0UV5xKhKvcALoG', 'SUPERADMIN', NULL,              TRUE),
    ('admin.bimbam',           '$2a$10$dCQrf7Tnf7EwT0CcrPBfRew9qSMHbquSiDhwirMeHeNid0qjGjTYq', 'ADMIN',      'bimbam',         TRUE),
    ('usuario.bimbam',         '$2a$10$X9J0VyumsO2dyajK9dKTH.jmz2.ke5QrVRMbhVHZUkpucuLVy9Juu', 'USUARIO',    'bimbam',         TRUE),
    ('admin.mercadocentral',   '$2a$10$UFDjobjcrQBhL9rbI0FfQeoeS/y18816994/O3nfOUfSCRe1yxKXe', 'ADMIN',      'mercado-central', TRUE),
    ('usuario.mercadocentral', '$2a$10$7LsrTRtOHTcbaUHxCJRELO3aiEhUf5SVBjxU0Osc3hDl8AQNa8iH6', 'USUARIO',    'mercado-central', TRUE),
    ('admin.santopegasus',     '$2a$10$BMGiYxT98XDiN7MZc0ikduAx19Np6QixV2rhe42u1x.jU8u7uV4Oq', 'ADMIN',      'santo-pegasus',  TRUE),
    ('usuario.santopegasus',   '$2a$10$GPxD.w6EPrVPicrWuV34r.rj5qb3ersebsd4sqvwXiI7xMFksUGE6', 'USUARIO',    'santo-pegasus',  TRUE);
