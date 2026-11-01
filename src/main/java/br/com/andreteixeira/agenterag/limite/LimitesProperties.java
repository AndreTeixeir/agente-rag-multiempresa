package br.com.andreteixeira.agenterag.limite;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Limites do chat público — {@code app.demo.limites.*} em
 * {@code application.yml}. Nunca hardcoded no código, mesma convenção de
 * {@link br.com.andreteixeira.agenterag.ingestao.IngestionProperties}.
 * <p>
 * {@code fuso} define quando o dia vira: à meia-noite desse fuso. Zero em um
 * dos limites diários bloqueia todas as perguntas; valor negativo é
 * configuração inválida e a aplicação não sobe. {@code tamanhoMaximoMensagem}
 * é o máximo de caracteres (unidades UTF-16, como o {@code maxlength} do
 * navegador) de uma mensagem, e precisa ser pelo menos 1.
 * <p>
 * {@code tentativasDeLoginPorIpPorHora} é o máximo de logins ERRADOS por IP em cada hora
 * (a hora do relógio, no mesmo fuso); a partir dele o login responde 429 até a hora virar.
 * <p>
 * {@code cadastrosPorDia} e {@code cadastrosPorIpPorDia} são o teto de contas criadas pela API de
 * administração, no total e por IP, em cada dia — excluir uma conta não devolve a vaga. Zero bloqueia
 * todos os cadastros.
 */
@ConfigurationProperties(prefix = "app.demo.limites")
public record LimitesProperties(
        int perguntasPorDia,
        int perguntasPorIpPorDia,
        ZoneId fuso,
        int tamanhoMaximoMensagem,
        int tentativasDeLoginPorIpPorHora,
        int cadastrosPorDia,
        int cadastrosPorIpPorDia) {

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
        if (tamanhoMaximoMensagem < 1) {
            throw new IllegalArgumentException(
                    "app.demo.limites.tamanho-maximo-mensagem precisa ser pelo menos 1: " + tamanhoMaximoMensagem);
        }
        if (tentativasDeLoginPorIpPorHora < 0) {
            throw new IllegalArgumentException(
                    "app.demo.limites.tentativas-de-login-por-ip-por-hora não pode ser negativo: " + tentativasDeLoginPorIpPorHora);
        }
        if (cadastrosPorDia < 0) {
            throw new IllegalArgumentException("app.demo.limites.cadastros-por-dia não pode ser negativo: " + cadastrosPorDia);
        }
        if (cadastrosPorIpPorDia < 0) {
            throw new IllegalArgumentException(
                    "app.demo.limites.cadastros-por-ip-por-dia não pode ser negativo: " + cadastrosPorIpPorDia);
        }
    }
}
