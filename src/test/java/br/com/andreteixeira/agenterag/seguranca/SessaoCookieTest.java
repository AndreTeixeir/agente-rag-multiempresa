package br.com.andreteixeira.agenterag.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/** Os atributos do cookie da sessão, no padrão e com {@code Secure} desligado. */
class SessaoCookieTest {

    private static SessaoCookie cookie(boolean seguro) {
        return new SessaoCookie(new AuthProperties("", seguro, Duration.ofHours(8)));
    }

    @Test
    void noPadraoOCookieEhHttpOnlySecureSameSiteStrictComPathRaizEOitoHoras() {
        String setCookie = cookie(true).criar("o-token");

        assertThat(setCookie)
                .startsWith("__Host-sabia-sessao=o-token;")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/")
                .contains("Max-Age=28800")
                .doesNotContain("Domain");
    }

    @Test
    void comSecureODomainFicaDeForaParaOPrefixoHostValer() {
        assertThat(cookie(true).nome()).isEqualTo("__Host-sabia-sessao");
        assertThat(cookie(true).criar("t")).doesNotContainIgnoringCase("domain");
    }

    @Test
    void semSecureNaoHaAtributoSecureNemPrefixoHostMasOResto() {
        String setCookie = cookie(false).criar("o-token");

        assertThat(cookie(false).nome()).isEqualTo("sabia-sessao");
        assertThat(setCookie)
                .startsWith("sabia-sessao=o-token;")
                .doesNotContain("Secure")
                .contains("HttpOnly")
                .contains("SameSite=Strict")
                .contains("Path=/");
    }

    @Test
    void oLogoutApagaOMesmoCookieComOsMesmosAtributos() {
        String setCookie = cookie(true).apagar();

        assertThat(setCookie)
                .startsWith("__Host-sabia-sessao=;")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/");
    }

    @Test
    void aDuracaoDaSessaoVemDaConfiguracao() {
        SessaoCookie umaHora = new SessaoCookie(new AuthProperties("", true, Duration.ofHours(1)));

        assertThat(umaHora.criar("t")).contains("Max-Age=3600");
    }
}
