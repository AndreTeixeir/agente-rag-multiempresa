-- Contadores dos limites diários do chat público (Etapa pós-10 — proteção da demo).
--
-- Uma linha por dia e por chave. A chave é 'total' (todas as perguntas do dia) ou
-- 'ip:<sha256 do IP em hexadecimal>' (as perguntas de cada IP). O IP nunca é gravado em
-- texto, só o hash dele — por isso a coluna comporta 'ip:' + 64 caracteres.
--
-- Os contadores vivem no banco, e não em memória, para sobreviver a reinício e deploy da
-- aplicação. A chave primária (dia, chave) é o que permite o incremento atômico por
-- INSERT ... ON CONFLICT DO UPDATE, seguro com requisições simultâneas, e também serve de
-- índice para a limpeza por dia.
--
-- Retenção: as linhas 'ip:' de dias anteriores são apagadas (só o dia corrente fica
-- guardado), e as linhas 'total' ficam 7 dias. A limpeza é feita pela aplicação
-- (LimiteDiarioService), não por esta migração.

CREATE TABLE limite_diario (
    dia      DATE        NOT NULL,
    chave    VARCHAR(80) NOT NULL,
    contagem INTEGER     NOT NULL CHECK (contagem >= 0),
    PRIMARY KEY (dia, chave)
);
