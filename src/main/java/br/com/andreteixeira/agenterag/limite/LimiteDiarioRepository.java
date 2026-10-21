package br.com.andreteixeira.agenterag.limite;

import java.time.LocalDate;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso à tabela {@code limite_diario} ({@code V4__limites_diarios.sql}) por
 * JDBC direto, como o {@code PgVectorStore} — não há entidade JPA.
 */
@Repository
public class LimiteDiarioRepository {

    /**
     * Incremento atômico e condicional num só comando — a mesma garantia vale
     * com qualquer número de requisições simultâneas. O {@code INSERT ... SELECT
     * ... WHERE} recusa também a criação da linha quando o limite é zero, e o
     * {@code WHERE} do {@code DO UPDATE} recusa o incremento acima do limite.
     * Sem linha de volta, o limite já estava atingido. Os {@code CAST} são
     * necessários: o Postgres não infere o tipo de um parâmetro solto no
     * {@code SELECT}.
     */
    private static final String INCREMENTAR_SE_ABAIXO_DO_LIMITE =
            """
            INSERT INTO limite_diario (dia, chave, contagem)
            SELECT CAST(:dia AS date), CAST(:chave AS varchar), 1
            WHERE CAST(:limite AS integer) >= 1
            ON CONFLICT (dia, chave) DO UPDATE
                SET contagem = limite_diario.contagem + 1
                WHERE limite_diario.contagem < CAST(:limite AS integer)
            RETURNING contagem
            """;

    private final JdbcClient jdbc;

    public LimiteDiarioRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Soma 1 à contagem de {@code chave} no {@code dia}, se ela ainda estiver
     * abaixo de {@code limite}. Devolve a nova contagem, ou vazio se o limite
     * já tinha sido atingido (e nada foi alterado).
     */
    public OptionalInt incrementarSeAbaixoDoLimite(LocalDate dia, String chave, int limite) {
        Optional<Integer> contagem = jdbc.sql(INCREMENTAR_SE_ABAIXO_DO_LIMITE)
                .param("dia", dia)
                .param("chave", chave)
                .param("limite", limite)
                .query(Integer.class)
                .optional();
        return contagem.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /** Apaga as contagens por IP (chaves {@code ip:*}) de dias anteriores a {@code dia}. */
    public int apagarContagensDeIpAnterioresA(LocalDate dia) {
        return jdbc.sql("DELETE FROM limite_diario WHERE dia < :dia AND chave LIKE 'ip:%'")
                .param("dia", dia)
                .update();
    }

    /** Apaga todas as contagens, de qualquer chave, de dias anteriores a {@code dia}. */
    public int apagarContagensAnterioresA(LocalDate dia) {
        return jdbc.sql("DELETE FROM limite_diario WHERE dia < :dia").param("dia", dia).update();
    }
}
