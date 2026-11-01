package br.com.andreteixeira.agenterag.seguranca;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Sessão por cookie — {@code app.auth.*} em {@code application.yml}.
 * <p>
 * {@code jwtSecret} vem de {@code APP_AUTH_JWT_SECRET} (placeholder explícito no yml: o
 * <i>relaxed binding</i> de uma variável com esse nome apontaria para {@code app.auth.jwt.secret},
 * não para {@code jwt-secret}). Vazio: o app gera um segredo aleatório a cada boot e avisa; com
 * menos de 32 bytes: o app não sobe (ver {@link SegurancaConfig#chaveDoToken}).
 * <p>
 * {@code cookieSeguro} liga o atributo {@code Secure} do cookie — padrão {@code true}. Os
 * navegadores aceitam cookie {@code Secure} em {@code http://localhost}, mas há quem não aceite
 * (Safari, ferramentas de linha de comando): quem roda sem HTTPS desliga com
 * {@code APP_AUTH_COOKIE_SEGURO=false}. A mesma propriedade decide o esquema esperado pela
 * conferência de {@code Origin} (ver {@link OrigemFilter}, que aceita {@code http} e {@code https}
 * no próprio loopback).
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @DefaultValue("") String jwtSecret,
        @DefaultValue("true") boolean cookieSeguro,
        @DefaultValue("8h") Duration duracaoDaSessao) {

    /**
     * Com {@code Secure} o nome leva o prefixo {@code __Host-}, que o navegador só aceita
     * com {@code Secure}, {@code Path=/} e sem {@code Domain}: um subdomínio irmão não consegue
     * plantar um cookie com esse nome para o domínio todo.
     */
    private static final String NOME_COM_PREFIXO_HOST = "__Host-sabia-sessao";
    private static final String NOME_SEM_PREFIXO = "sabia-sessao";

    public AuthProperties {
        if (jwtSecret == null) {
            jwtSecret = "";
        }
        if (duracaoDaSessao == null || duracaoDaSessao.isZero() || duracaoDaSessao.isNegative()) {
            throw new IllegalArgumentException("app.auth.duracao-da-sessao precisa ser positiva: " + duracaoDaSessao);
        }
    }

    public String nomeDoCookie() {
        return cookieSeguro ? NOME_COM_PREFIXO_HOST : NOME_SEM_PREFIXO;
    }

    /** O segredo nunca vai para um log, nem por acidente. */
    @Override
    public String toString() {
        return "AuthProperties[jwtSecret=***, cookieSeguro=" + cookieSeguro + ", duracaoDaSessao=" + duracaoDaSessao + "]";
    }
}
