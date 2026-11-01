package br.com.andreteixeira.agenterag;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code spring.jpa.open-in-view} desligado, e a prova de que nada depende dele: o aplicativo não tem
 * nenhuma entidade JPA (o acesso ao banco é todo por JDBC), então não há carregamento preguiçoso que
 * precise do {@code EntityManager} aberto durante a requisição.
 */
@SpringBootTest
class OpenInViewDesligadoTest {

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        PostgresDeTeste.registrar(registry);
    }

    @Autowired
    private Environment ambiente;

    @Autowired
    private ApplicationContext contexto;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void aPropriedadeEstaDesligada() {
        assertThat(ambiente.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
    }

    @Test
    void oInterceptadorDoOpenInViewNaoFoiRegistrado() {
        assertThat(contexto.getBeansOfType(OpenEntityManagerInViewInterceptor.class)).isEmpty();
    }

    @Test
    void naoHaEntidadeJpaNenhuma() {
        assertThat(entityManagerFactory.getMetamodel().getEntities()).isEmpty();
    }
}
