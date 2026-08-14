package br.com.andreteixeira.agenterag.ingestao;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Gatilho de produção: perfil {@code ingest} — ingere e encerra. Nunca ativo
 * no perfil web (não há {@code @Profile("ingest")} sem passar
 * {@code --spring.profiles.active=ingest} explicitamente), então a ingestão
 * nunca roda no boot da aplicação web.
 */
@Component
@Profile("ingest")
public class IngestionRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IngestionRunner.class);

    private final IngestionService ingestionService;
    private final ConfigurableApplicationContext applicationContext;

    public IngestionRunner(IngestionService ingestionService, ConfigurableApplicationContext applicationContext) {
        this.ingestionService = ingestionService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean force = args.containsOption("force");
        int codigoSaida = executarIngestao(force);
        System.exit(SpringApplication.exit(applicationContext, () -> codigoSaida));
    }

    private int executarIngestao(boolean force) {
        try {
            ingestionService.ingest(force);
            return 0;
        } catch (RuntimeException e) {
            log.error("Ingestão falhou.", e);
            return 1;
        }
    }
}
