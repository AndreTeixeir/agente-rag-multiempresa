package br.com.andreteixeira.agenterag.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link ContaRepository} contra o Postgres real, com o contexto da aplicação inteiro (o Flyway
 * aplica a V5 no boot). Nenhuma chamada paga.
 */
@SpringBootTest
class ContaRepositoryIntegrationTest {

    private static final String HASH = "$2a$10$nm3cEiA4oBn8yX2Tn71moeBbU9JvkpSQ8yCxECD0UV5xKhKvcALoG";

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        PostgresDeTeste.registrar(registry);
    }

    @Autowired
    private ContaRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void apagarOQueOTesteCriou() {
        jdbc.sql("DELETE FROM conta WHERE NOT semeada").update();
    }

    @Test
    void buscaUmaContaSemeadaPeloUsuario() {
        Conta conta = repository.buscarPorUsuario("admin.bimbam").orElseThrow();

        assertThat(conta.papel()).isEqualTo(Papel.ADMIN);
        assertThat(conta.empresa()).isEqualTo("bimbam");
        assertThat(conta.semeada()).isTrue();
        assertThat(conta.superadminReal()).isFalse();
        assertThat(conta.protegida()).isTrue();
        assertThat(conta.criadaPor()).isNull();
        assertThat(conta.criadaEm()).isNotNull();
    }

    @Test
    void superadminDeDemonstracaoNaoTemEmpresa() {
        Conta conta = repository.buscarPorUsuario("superadmin.demo").orElseThrow();

        assertThat(conta.papel()).isEqualTo(Papel.SUPERADMIN);
        assertThat(conta.empresa()).isNull();
    }

    @Test
    void usuarioInexistenteNaoAchaNada() {
        assertThat(repository.buscarPorUsuario("ninguem")).isEmpty();
        assertThat(repository.buscarPorId(-1)).isEmpty();
    }

    @Test
    void insereEBuscaPeloId() {
        long id = repository.inserir("nova.conta", HASH, Papel.USUARIO, "santo-pegasus", false, false, null);

        Conta conta = repository.buscarPorId(id).orElseThrow();
        assertThat(conta.usuario()).isEqualTo("nova.conta");
        assertThat(conta.papel()).isEqualTo(Papel.USUARIO);
        assertThat(conta.empresa()).isEqualTo("santo-pegasus");
        assertThat(conta.protegida()).isFalse();
    }

    @Test
    void usuarioRepetidoLancaDuplicateKey() {
        repository.inserir("repetida", HASH, Papel.USUARIO, "bimbam", false, false, null);

        assertThatThrownBy(() -> repository.inserir("repetida", HASH, Papel.USUARIO, "bimbam", false, false, null))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void contaCriadaPeloSuperadminRealEhProtegidaMasNaoSemeada() {
        long idDoReal = repository.inserir("o.real", HASH, Papel.SUPERADMIN, null, false, true, null);
        long idDaCriada = repository.inserir("criada.pelo.real", HASH, Papel.USUARIO, "bimbam", false, false, idDoReal);

        Conta criada = repository.buscarPorId(idDaCriada).orElseThrow();
        assertThat(criada.criadaPor()).isEqualTo(idDoReal);
        assertThat(criada.criadaPelaReal()).isTrue();
        assertThat(criada.semeada()).isFalse();
        assertThat(criada.protegida()).isTrue();
    }

    @Test
    void contaCriadaPorUmaContaComumNaoEhProtegida() {
        long idDoAdmin = repository.buscarPorUsuario("admin.bimbam").orElseThrow().id();
        long idDaCriada = repository.inserir("criada.pelo.admin", HASH, Papel.USUARIO, "bimbam", false, false, idDoAdmin);

        Conta criada = repository.buscarPorId(idDaCriada).orElseThrow();
        assertThat(criada.criadaPelaReal()).isFalse();
        assertThat(criada.protegida()).isFalse();
    }

    @Test
    void excluirDevolveSeHaviaAConta() {
        long id = repository.inserir("para.excluir", HASH, Papel.USUARIO, "bimbam", false, false, null);

        assertThat(repository.excluir(id)).isTrue();
        assertThat(repository.excluir(id)).isFalse();
        assertThat(repository.buscarPorId(id)).isEmpty();
    }

    @Test
    void oToStringDaContaNaoMostraOHash() {
        Conta conta = repository.buscarPorUsuario("usuario.bimbam").orElseThrow();

        assertThat(conta.toString()).contains("usuario.bimbam").doesNotContain("$2a$").doesNotContain(conta.senhaHash());
    }
}
