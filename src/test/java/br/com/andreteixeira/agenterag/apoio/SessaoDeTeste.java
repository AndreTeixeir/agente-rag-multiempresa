package br.com.andreteixeira.agenterag.apoio;

import static org.mockito.Mockito.when;

import br.com.andreteixeira.agenterag.conta.AutenticacaoService;
import br.com.andreteixeira.agenterag.conta.Conta;
import br.com.andreteixeira.agenterag.conta.Papel;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Sessão de mentira para os testes de fatia ({@code @WebMvcTest}), onde o {@link TokenService} e o
 * {@link AutenticacaoService} são mocks: o cookie leva um token fixo, e o mock "valida" esse token
 * como o de uma conta. A cadeia de segurança de verdade (filtros, rotas, 401/403) roda do mesmo jeito.
 */
public final class SessaoDeTeste {

    public static final String NOME_DO_COOKIE = "__Host-sabia-sessao";
    public static final String TOKEN = "token-de-teste";

    private SessaoDeTeste() {
    }

    public static Cookie cookie() {
        return new Cookie(NOME_DO_COOKIE, TOKEN);
    }

    public static Conta conta(long id, String usuario, Papel papel, String empresa) {
        return new Conta(id, usuario, "hash-que-nao-importa", papel, empresa, false, false, null, false, Instant.parse("2026-10-21T15:00:00Z"));
    }

    public static Conta usuarioBimbam() {
        return conta(7, "usuario.teste", Papel.USUARIO, "bimbam");
    }

    public static Conta superadmin() {
        return conta(7, "superadmin.teste", Papel.SUPERADMIN, null);
    }

    /** O superadmin real: fora dos limites de perguntas. */
    public static Conta superadminReal() {
        return new Conta(7, "real.teste", "hash-que-nao-importa", Papel.SUPERADMIN, null, false, true, null, false, Instant.parse("2026-10-21T15:00:00Z"));
    }

    /** Faz os mocks reconhecerem o cookie de {@link #cookie()} como a sessão de {@code conta}. */
    public static void entrarComo(TokenService tokens, AutenticacaoService autenticacao, Conta conta) {
        when(tokens.validar(TOKEN)).thenReturn(OptionalLong.of(conta.id()));
        when(autenticacao.contaDaSessao(conta.id())).thenReturn(Optional.of(conta));
    }
}
