package br.com.andreteixeira.agenterag.limite;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites diários do chat público — {@code app.demo.limites.*} em
 * {@code application.yml}. Nunca hardcoded no código, mesma convenção de
 * {@link br.com.andreteixeira.agenterag.ingestao.IngestionProperties}.
 * <p>
 * {@code fuso} define quando o dia vira: à meia-noite desse fuso. Zero em um
 * dos limites bloqueia todas as perguntas; valor negativo é configuração
 * inválida e a aplicação não sobe.
 */
@ConfigurationProperties(prefix = "app.demo.limites")
public record LimitesProperties(int perguntasPorDia, int perguntasPorIpPorDia, ZoneId fuso) {

    public LimitesProperties {
        if (perguntasPorDia < 0) {
            throw new IllegalArgumentException("app.demo.limites.perguntas-por-dia não pode ser negativo: " + perguntasPorDia);
        }
        if (perguntasPorIpPorDia < 0) {
            throw new IllegalArgumentException(
                    "app.demo.limites.perguntas-por-ip-por-dia não pode ser negativo: " + perguntasPorIpPorDia);
        }
        if (fuso == null) {
            throw new IllegalArgumentException("app.demo.limites.fuso é obrigatório (ex.: America/Sao_Paulo)");
        }
    }
}
