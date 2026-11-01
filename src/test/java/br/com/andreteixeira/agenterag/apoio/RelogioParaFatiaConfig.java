package br.com.andreteixeira.agenterag.apoio;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * O {@link Clock} que os testes de fatia ({@code @WebMvcTest}) não têm: lá o {@code LimiteDiarioConfig},
 * que o declara, não é carregado, e a configuração de segurança precisa dele para validar o token.
 */
@TestConfiguration
public class RelogioParaFatiaConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
