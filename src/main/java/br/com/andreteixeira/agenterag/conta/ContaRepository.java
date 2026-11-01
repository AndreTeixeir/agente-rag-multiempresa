package br.com.andreteixeira.agenterag.conta;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Acesso à tabela {@code conta} ({@code V5__contas.sql}) por JDBC direto, como o
 * {@code LimiteDiarioRepository} e o {@code PgVectorStore} — não há entidade JPA.
 */
@Repository
public class ContaRepository {

    /**
     * {@code criada_pela_real} é calculada: a conta que criou esta é o superadmin real?
     * Fica numa subconsulta (e não numa coluna) para nunca ficar fora de sincronia com
     * {@code criada_por}.
     */
    private static final String SELECIONAR =
            """
            SELECT c.id, c.usuario, c.senha_hash, c.papel, c.empresa, c.semeada, c.superadmin_real,
                   c.criada_por, c.criada_em,
                   EXISTS (SELECT 1 FROM conta r WHERE r.id = c.criada_por AND r.superadmin_real) AS criada_pela_real
            FROM conta c
            """;

    private final JdbcClient jdbc;

    public ContaRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Conta> buscarPorId(long id) {
        return jdbc.sql(SELECIONAR + " WHERE c.id = :id")
                .param("id", id)
                .query(ContaRepository::mapear)
                .optional();
    }

    /** O {@code usuario} é comparado como está: quem chama normaliza (minúsculas) antes. */
    public Optional<Conta> buscarPorUsuario(String usuario) {
        return jdbc.sql(SELECIONAR + " WHERE c.usuario = :usuario")
                .param("usuario", usuario)
                .query(ContaRepository::mapear)
                .optional();
    }

    /**
     * Cria a conta e devolve o id. Usuário repetido lança {@code DuplicateKeyException}; um
     * valor que o banco não aceita (formato, papel, empresa fora do papel) lança
     * {@code DataIntegrityViolationException}.
     */
    public long inserir(
            String usuario,
            String senhaHash,
            Papel papel,
            String empresa,
            boolean semeada,
            boolean superadminReal,
            Long criadaPor) {
        return jdbc.sql(
                        """
                        INSERT INTO conta (usuario, senha_hash, papel, empresa, semeada, superadmin_real, criada_por)
                        VALUES (:usuario, :senhaHash, :papel, :empresa, :semeada, :superadminReal, :criadaPor)
                        RETURNING id
                        """)
                .param("usuario", usuario)
                .param("senhaHash", senhaHash)
                .param("papel", papel.name())
                .param("empresa", empresa)
                .param("semeada", semeada)
                .param("superadminReal", superadminReal)
                .param("criadaPor", criadaPor)
                .query(Long.class)
                .single();
    }

    /** Todas as contas menos o superadmin real, que nunca aparece em listagem. */
    public List<Conta> listarTodasExcetoReal() {
        return jdbc.sql(SELECIONAR + " WHERE NOT c.superadmin_real ORDER BY c.empresa NULLS FIRST, c.papel, c.usuario")
                .query(ContaRepository::mapear)
                .list();
    }

    /** Os usuários comuns (papel USUARIO) de uma empresa. */
    public List<Conta> listarUsuariosDaEmpresa(String empresa) {
        return jdbc.sql(SELECIONAR + " WHERE c.papel = 'USUARIO' AND c.empresa = :empresa ORDER BY c.usuario")
                .param("empresa", empresa)
                .query(ContaRepository::mapear)
                .list();
    }

    /** O superadmin real, se já foi criado por algum boot; no máximo um (índice parcial único). */
    public Optional<Conta> buscarSuperadminReal() {
        return jdbc.sql(SELECIONAR + " WHERE c.superadmin_real").query(ContaRepository::mapear).optional();
    }

    /** Troca o usuário e o hash do superadmin real. Usuário que já é de outra conta lança {@code DuplicateKeyException}. */
    public void atualizarSuperadminReal(long id, String usuario, String senhaHash) {
        jdbc.sql("UPDATE conta SET usuario = :usuario, senha_hash = :senhaHash WHERE id = :id AND superadmin_real")
                .param("id", id)
                .param("usuario", usuario)
                .param("senhaHash", senhaHash)
                .update();
    }

    /**
     * O reset diário: apaga toda conta que não é semeada, não é o superadmin real e não foi criada
     * por ele. Devolve quantas. As protegidas ficam; o que uma conta apagada tenha criado (e não seja
     * protegido) sai junto, na mesma instrução.
     */
    public int apagarContasDeDemonstracao() {
        return jdbc.sql(
                        """
                        DELETE FROM conta c
                        WHERE NOT c.semeada
                          AND NOT c.superadmin_real
                          AND NOT EXISTS (SELECT 1 FROM conta r WHERE r.id = c.criada_por AND r.superadmin_real)
                        """)
                .update();
    }

    /** Devolve se havia uma conta com esse id. */
    public boolean excluir(long id) {
        return jdbc.sql("DELETE FROM conta WHERE id = :id").param("id", id).update() > 0;
    }

    private static Conta mapear(ResultSet rs, int linha) throws SQLException {
        return new Conta(
                rs.getLong("id"),
                rs.getString("usuario"),
                rs.getString("senha_hash"),
                Papel.valueOf(rs.getString("papel")),
                rs.getString("empresa"),
                rs.getBoolean("semeada"),
                rs.getBoolean("superadmin_real"),
                rs.getObject("criada_por", Long.class),
                rs.getBoolean("criada_pela_real"),
                rs.getTimestamp("criada_em").toInstant());
    }
}
