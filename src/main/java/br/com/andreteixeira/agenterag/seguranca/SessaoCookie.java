package br.com.andreteixeira.agenterag.seguranca;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * O cookie da sessão: {@code HttpOnly} (o JavaScript da página não o lê), {@code Secure} (padrão),
 * {@code SameSite=Strict}, {@code Path=/}, sem {@code Domain} (vale só para este host) e com a
 * duração de {@link AuthProperties#duracaoDaSessao()}. O logout devolve o mesmo cookie, vazio e
 * com {@code Max-Age=0}: os atributos precisam ser os mesmos para o navegador apagar o certo.
 */
@Component
public class SessaoCookie {

    private final AuthProperties auth;

    public SessaoCookie(AuthProperties auth) {
        this.auth = auth;
    }

    public String nome() {
        return auth.nomeDoCookie();
    }

    /** O valor do cabeçalho {@code Set-Cookie} que abre a sessão. */
    public String criar(String token) {
        return base(token).maxAge(auth.duracaoDaSessao()).build().toString();
    }

    /** O valor do cabeçalho {@code Set-Cookie} que encerra a sessão. */
    public String apagar() {
        return base("").maxAge(0).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(auth.nomeDoCookie(), valor)
                .httpOnly(true)
                .secure(auth.cookieSeguro())
                .sameSite("Strict")
                .path("/");
    }
}
