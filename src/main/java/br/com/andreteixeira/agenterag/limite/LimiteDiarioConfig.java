package br.com.andreteixeira.agenterag.limite;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Wiring dos limites diários. {@code @EnableScheduling} existe só para a
 * limpeza agendada de {@link LimiteDiarioService#limparAntigosAgendado()}.
 * O {@link Clock} é um bean para os testes poderem trocá-lo e fazer o dia
 * virar sem esperar; o fuso é aplicado por quem o usa, a partir de
 * {@link LimitesProperties#fuso()}.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(LimitesProperties.class)
public class LimiteDiarioConfig {

    @Bean
    public Clock relogio() {
        return Clock.systemUTC();
    }
}
