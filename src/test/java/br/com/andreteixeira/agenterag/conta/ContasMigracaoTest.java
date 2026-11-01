package br.com.andreteixeira.agenterag.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * A migração {@code V5__contas.sql} num banco ZERADO: um banco novo dentro do contêiner, onde o
 * Flyway aplica de V1 a V5 do zero (e não um banco já migrado por outro teste). Sem contexto
 * Spring — só o Flyway e o JDBC —, então o teste não depende do que a aplicação faz no boot.
 */
class ContasMigracaoTest {

    private static final String HASH_QUALQUER = "$2a$10$nm3cEiA4oBn8yX2Tn71moeBbU9JvkpSQ8yCxECD0UV5xKhKvcALoG";

    private static JdbcClient jdbc;

    @BeforeAll
    static void migrarNumBancoZerado() {
        String banco = "migracao_v5_" + UUID.randomUUID().toString().replace("-", "");
        String urlDoBancoDeTeste = PostgresDeTeste.POSTGRES.getJdbcUrl();
        String usuario = PostgresDeTeste.POSTGRES.getUsername();
        String senha = PostgresDeTeste.POSTGRES.getPassword();

        JdbcClient.create(new DriverManagerDataSource(urlDoBancoDeTeste, usuario, senha))
                .sql("CREATE DATABASE " + banco)
                .update();

        String urlDoBancoNovo = urlDoBancoDeTeste.replace("/" + PostgresDeTeste.POSTGRES.getDatabaseName(), "/" + banco);
        Flyway.configure()
                .dataSource(urlDoBancoNovo, usuario, senha)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        jdbc = JdbcClient.create(new DriverManagerDataSource(urlDoBancoNovo, usuario, senha));
    }

    @AfterEach
    void apagarOQueOTesteCriou() {
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
    }

    @Test
    void aMigracaoV5FoiAplicadaComSucesso() {
        Boolean sucesso = jdbc.sql("SELECT success FROM flyway_schema_history WHERE version = '5'")
                .query(Boolean.class)
                .single();

        assertThat(sucesso).isTrue();
    }

    @Test
    void criaAsSeteContasDeDemonstracao() {
        List<String> contas = jdbc.sql(
                        """
                        SELECT usuario || '|' || papel || '|' || COALESCE(empresa, '(nenhuma)') || '|' || semeada || '|' || superadmin_real
                        FROM conta ORDER BY usuario
                        """)
                .query(String.class)
                .list();

        assertThat(contas)
                .containsExactly(
                        "admin.bimbam|ADMIN|bimbam|true|false",
                        "admin.mercadocentral|ADMIN|mercado-central|true|false",
                        "admin.santopegasus|ADMIN|santo-pegasus|true|false",
                        "superadmin.demo|SUPERADMIN|(nenhuma)|true|false",
                        "usuario.bimbam|USUARIO|bimbam|true|false",
                        "usuario.mercadocentral|USUARIO|mercado-central|true|false",
                        "usuario.santopegasus|USUARIO|santo-pegasus|true|false");
    }

    @Test
    void asSenhasDasContasSemeadasSaoBCryptDaSenhaPublica() {
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
        List<String> hashes = jdbc.sql("SELECT senha_hash FROM conta WHERE semeada").query(String.class).list();

        assertThat(hashes).hasSize(7);
        assertThat(hashes).allSatisfy(hash -> {
            assertThat(hash).startsWith("$2a$10$").doesNotContain("sabia-demo");
            assertThat(bcrypt.matches("sabia-demo", hash)).isTrue();
            assertThat(bcrypt.matches("outra-senha", hash)).isFalse();
        });
    }

    @Test
    void osSeteHashesSaoDistintos() {
        Integer distintos = jdbc.sql("SELECT COUNT(DISTINCT senha_hash) FROM conta WHERE semeada")
                .query(Integer.class)
                .single();

        assertThat(distintos).as("um hash (com sal próprio) por conta").isEqualTo(7);
    }

    @Test
    void superadminNaoTemEmpresa() {
        assertThatThrownBy(() -> inserir("superadmin.com.empresa", "SUPERADMIN", "bimbam", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuarioEAdminPrecisamDeEmpresa() {
        assertThatThrownBy(() -> inserir("usuario.sem.empresa", "USUARIO", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir("admin.sem.empresa", "ADMIN", null, false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuarioEUnico() {
        inserir("conta.repetida", "USUARIO", "bimbam", false);

        assertThatThrownBy(() -> inserir("conta.repetida", "ADMIN", "santo-pegasus", false))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void usuarioPrecisaTerOFormatoEmMinusculas() {
        assertThatThrownBy(() -> inserir("Maiuscula", "USUARIO", "bimbam", false))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir("ab", "USUARIO", "bimbam", false)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> inserir("com espaco", "USUARIO", "bimbam", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void papelPrecisaSerUmDosTres() {
        assertThatThrownBy(() -> inserir("papel.invalido", "DONO", "bimbam", false))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void soSuperadminPodeSerOReal() {
        assertThatThrownBy(() -> inserir("usuario.real", "USUARIO", "bimbam", true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void haNoMaximoUmSuperadminReal() {
        inserir("primeiro.real", "SUPERADMIN", null, true);

        assertThatThrownBy(() -> inserir("segundo.real", "SUPERADMIN", null, true))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void excluirQuemCriouDeixaACriadaPorNula() {
        inserir("criadora", "ADMIN", "bimbam", false);
        long idDaCriadora = idDe("criadora");
        jdbc.sql(
                        """
                        INSERT INTO conta (usuario, senha_hash, papel, empresa, criada_por)
                        VALUES ('criada', :hash, 'USUARIO', 'bimbam', :criadora)
                        """)
                .param("hash", HASH_QUALQUER)
                .param("criadora", idDaCriadora)
                .update();

        jdbc.sql("DELETE FROM conta WHERE id = :id").param("id", idDaCriadora).update();

        Boolean criadaPorEstaNula = jdbc.sql("SELECT criada_por IS NULL FROM conta WHERE usuario = 'criada'")
                .query(Boolean.class)
                .single();
        assertThat(criadaPorEstaNula).isTrue();
    }

    private static void inserir(String usuario, String papel, String empresa, boolean real) {
        jdbc.sql(
                        """
                        INSERT INTO conta (usuario, senha_hash, papel, empresa, superadmin_real)
                        VALUES (:usuario, :hash, :papel, :empresa, :real)
                        """)
                .param("usuario", usuario)
                .param("hash", HASH_QUALQUER)
                .param("papel", papel)
                .param("empresa", empresa)
                .param("real", real)
                .update();
    }

    private static long idDe(String usuario) {
        return jdbc.sql("SELECT id FROM conta WHERE usuario = :usuario")
                .param("usuario", usuario)
                .query(Long.class)
                .single();
    }
}
