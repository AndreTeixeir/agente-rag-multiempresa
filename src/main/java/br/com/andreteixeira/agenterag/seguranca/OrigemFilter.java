package br.com.andreteixeira.agenterag.seguranca;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A segunda barreira contra CSRF (a primeira é o {@code SameSite=Strict} do cookie): em toda
 * requisição que muda estado (POST, PUT, PATCH, DELETE), se o navegador mandou {@code Origin},
 * ele tem de ser o do próprio site — {@code <esquema>://<Host da requisição>} — senão 403.
 * <p>
 * Por que não basta o {@code SameSite}: ele protege contra outros <i>sites</i>, mas
 * {@code sentiment.andreteixeira.dev.br} é do mesmo site que {@code andreteixeira.dev.br}, e uma
 * página de lá consegue mandar uma requisição com o cookie. O {@code Origin} dela é outro, e é
 * isso que esta conferência barra — inclusive no login (<i>login CSRF</i>).
 * <p>
 * Decisões:
 * <ul>
 *     <li>O esquema esperado vem de {@code app.auth.cookie-seguro} ({@code https} se
 *     {@code true}), e o host, do cabeçalho {@code Host} bruto. Não depende de a aplicação
 *     descobrir o esquema por {@code X-Forwarded-Proto}.</li>
 *     <li>Exceção: quando o host é o próprio loopback ({@code localhost}, {@code 127.0.0.1},
 *     {@code [::1]}), {@code http} e {@code https} valem — é como se roda no notebook, por
 *     {@code http://localhost}, com o padrão de {@code Secure} ligado (os navegadores aceitam o cookie
 *     {@code Secure} no localhost). Em produção o host nunca é loopback, e o esquema continua estrito.</li>
 *     <li>Sem {@code Origin} a requisição passa: todo navegador manda {@code Origin} em requisição
 *     que muda estado, então a ausência significa um cliente que não é navegador (curl, teste) —
 *     que não tem cookie de navegador a explorar.</li>
 *     <li>{@code Origin: null} (contextos opacos, como iframe isolado) é recusado: não é o site.</li>
 * </ul>
 */
final class OrigemFilter extends OncePerRequestFilter {

    static final String MENSAGEM = "Origem da requisição não permitida.";

    private static final Logger log = LoggerFactory.getLogger(OrigemFilter.class);
    private static final Set<String> METODOS_QUE_MUDAM_ESTADO = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final String esquema;

    OrigemFilter(AuthProperties auth) {
        this.esquema = auth.cookieSeguro() ? "https" : "http";
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String origem = request.getHeader("Origin");
        if (origem != null && METODOS_QUE_MUDAM_ESTADO.contains(request.getMethod())) {
            String host = request.getHeader("Host");
            if (host == null || !origemConfere(origem, host)) {
                // O Origin é dado do cliente: sem quebra de linha nem tamanho absurdo no log.
                log.warn("Requisição recusada: Origin '{}' diferente do esperado '{}' ({} {})",
                        limpar(origem), host == null ? null : limpar(esquema + "://" + host), request.getMethod(), request.getRequestURI());
                RespostasDeAcesso.escrever(response, HttpServletResponse.SC_FORBIDDEN, MENSAGEM);
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean origemConfere(String origem, String host) {
        if ((esquema + "://" + host).equalsIgnoreCase(origem)) {
            return true;
        }
        return ehLoopback(host)
                && (("http://" + host).equalsIgnoreCase(origem) || ("https://" + host).equalsIgnoreCase(origem));
    }

    /** {@code localhost}, {@code 127.0.0.1} ou {@code [::1]}, com ou sem porta. Não casa {@code localhost.outro.site}. */
    static boolean ehLoopback(String host) {
        String semPorta;
        if (host.startsWith("[")) {
            int fim = host.indexOf(']');
            semPorta = fim < 0 ? host : host.substring(0, fim + 1);
        } else {
            int doisPontos = host.indexOf(':');
            semPorta = doisPontos < 0 ? host : host.substring(0, doisPontos);
        }
        String nome = semPorta.toLowerCase(java.util.Locale.ROOT);
        return nome.equals("localhost") || nome.equals("127.0.0.1") || nome.equals("[::1]");
    }

    private static String limpar(String valor) {
        String semControle = valor.replaceAll("\\p{Cntrl}", "?");
        return semControle.length() > 200 ? semControle.substring(0, 200) + "..." : semControle;
    }
}
