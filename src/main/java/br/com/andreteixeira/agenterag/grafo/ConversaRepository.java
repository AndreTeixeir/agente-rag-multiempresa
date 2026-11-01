package br.com.andreteixeira.agenterag.grafo;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * As conversas do grafo, apagadas por SQL: o {@code PostgresSaver} não tem operação de exclusão. Os
 * checkpoints saem junto, pelo {@code ON DELETE CASCADE} de {@code LG4JCheckpoint}
 * ({@code V3__grafo_checkpoints.sql}). O {@code PostgresSaver} 1.9.0-beta2 não guarda nada das
 * conversas em memória — consulta o banco a cada acesso —, então apagar as linhas basta: a próxima
 * mensagem de uma conversa apagada começa uma conversa nova (ver {@code ChaveDaConversa}).
 */
@Repository
public class ConversaRepository {

    private final JdbcClient jdbc;

    public ConversaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** O reset diário: apaga todas as conversas, de todas as contas. Devolve quantas. */
    public int apagarTodas() {
        return jdbc.sql("DELETE FROM LG4JThread").update();
    }

    /** Apaga as conversas de uma conta (as threads cujo nome começa com o prefixo dela). Devolve quantas. */
    public int apagarDaConta(long contaId) {
        return jdbc.sql("DELETE FROM LG4JThread WHERE thread_name LIKE :prefixo")
                .param("prefixo", ChaveDaConversa.prefixoDaConta(contaId) + "%")
                .update();
    }
}
