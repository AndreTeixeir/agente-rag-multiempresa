package br.com.andreteixeira.agenterag.seguranca;

import br.com.andreteixeira.agenterag.conta.AutenticacaoService;
import br.com.andreteixeira.agenterag.conta.Conta;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lê o cookie da sessão e, se o token vale e a conta ainda existe, deixa a requisição autenticada
 * como essa conta. Cookie ausente, token inválido ou expirado, ou conta que já não existe: segue
 * sem autenticação, e a autorização por rota responde 401 onde o login for exigido.
 * <p>
 * Recarrega a conta do banco a cada requisição (um {@code SELECT} por chave primária): o papel e a
 * empresa nunca vêm do token nem do corpo, e excluir uma conta encerra a sessão dela na hora.
 */
final class SessaoCookieFilter extends OncePerRequestFilter {

    private final String nomeDoCookie;
    private final TokenService tokens;
    private final AutenticacaoService autenticacao;

    SessaoCookieFilter(AuthProperties auth, TokenService tokens, AutenticacaoService autenticacao) {
        this.nomeDoCookie = auth.nomeDoCookie();
        this.tokens = tokens;
        this.autenticacao = autenticacao;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = valorDoCookie(request);
        if (token != null) {
            OptionalLong id = tokens.validar(token);
            if (id.isPresent()) {
                Optional<Conta> conta = autenticacao.contaDaSessao(id.getAsLong());
                if (conta.isPresent()) {
                    SecurityContext contexto = SecurityContextHolder.createEmptyContext();
                    contexto.setAuthentication(ContaAutenticada.de(conta.get()).comoAutenticacao());
                    SecurityContextHolder.setContext(contexto);
                }
            }
        }
        chain.doFilter(request, response);
    }

    private String valorDoCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (nomeDoCookie.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
