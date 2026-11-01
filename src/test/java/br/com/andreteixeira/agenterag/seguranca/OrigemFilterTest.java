package br.com.andreteixeira.agenterag.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * A segunda barreira contra CSRF, sem servidor: o filtro sozinho, com requisições de mentira. O que
 * ele faz de verdade, atrás da cadeia inteira, está no {@code AuthIntegrationTest}.
 */
class OrigemFilterTest {

    private final OrigemFilter seguro = new OrigemFilter(new AuthProperties("", true, Duration.ofHours(8)));
    private final OrigemFilter semHttps = new OrigemFilter(new AuthProperties("", false, Duration.ofHours(8)));

    private static MockHttpServletRequest requisicao(String metodo, String host, String origem) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, "/api/auth/login");
        if (host != null) {
            request.addHeader("Host", host);
        }
        if (origem != null) {
            request.addHeader("Origin", origem);
        }
        return request;
    }

    private record Resultado(MockHttpServletResponse resposta, MockFilterChain cadeia) {

        boolean passou() {
            return cadeia.getRequest() != null;
        }
    }

    private static Resultado aplicar(OrigemFilter filtro, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        MockFilterChain cadeia = new MockFilterChain();
        filtro.doFilter(request, resposta, cadeia);
        return new Resultado(resposta, cadeia);
    }

    @Test
    void originIgualAoDoSitePassa() throws Exception {
        Resultado resultado =
                aplicar(seguro, requisicao("POST", "andreteixeira.dev.br", "https://andreteixeira.dev.br"));

        assertThat(resultado.passou()).isTrue();
        assertThat(resultado.resposta().getStatus()).isEqualTo(200);
    }

    @Test
    void originDeOutroSubdominioDoMesmoSiteEhBarrado() throws Exception {
        Resultado resultado = aplicar(
                seguro, requisicao("POST", "andreteixeira.dev.br", "https://sentiment.andreteixeira.dev.br"));

        assertThat(resultado.passou()).isFalse();
        assertThat(resultado.resposta().getStatus()).isEqualTo(403);
        assertThat(resultado.resposta().getContentAsString())
                .isEqualTo("{\"erro\":\"Origem da requisição não permitida.\"}");
        assertThat(resultado.resposta().getContentType()).startsWith("application/json");
    }

    @Test
    void originDeOutroSiteEhBarrado() throws Exception {
        Resultado resultado = aplicar(seguro, requisicao("POST", "andreteixeira.dev.br", "https://site-malicioso.example"));

        assertThat(resultado.passou()).isFalse();
        assertThat(resultado.resposta().getStatus()).isEqualTo(403);
    }

    @Test
    void originNullDeContextoOpacoEhBarrado() throws Exception {
        Resultado resultado = aplicar(seguro, requisicao("POST", "andreteixeira.dev.br", "null"));

        assertThat(resultado.passou()).isFalse();
        assertThat(resultado.resposta().getStatus()).isEqualTo(403);
    }

    @Test
    void semOriginPassaPorqueNaoEhNavegador() throws Exception {
        Resultado resultado = aplicar(seguro, requisicao("POST", "andreteixeira.dev.br", null));

        assertThat(resultado.passou()).isTrue();
    }

    @Test
    void comSecureOEsquemaEsperadoEHttpsEHttpNaoPassa() throws Exception {
        Resultado resultado = aplicar(seguro, requisicao("POST", "andreteixeira.dev.br", "http://andreteixeira.dev.br"));

        assertThat(resultado.passou()).isFalse();
        assertThat(resultado.resposta().getStatus()).isEqualTo(403);
    }

    @Test
    void semSecureOEsquemaEsperadoEHttpEHttpsNaoPassa() throws Exception {
        assertThat(aplicar(semHttps, requisicao("POST", "sabia.exemplo.test", "http://sabia.exemplo.test")).passou()).isTrue();
        assertThat(aplicar(semHttps, requisicao("POST", "sabia.exemplo.test", "https://sabia.exemplo.test")).passou()).isFalse();
    }

    @Test
    void aPortaFazParteDaOrigem() throws Exception {
        assertThat(aplicar(semHttps, requisicao("POST", "sabia.exemplo.test:8080", "http://sabia.exemplo.test:9090")).passou()).isFalse();
        assertThat(aplicar(semHttps, requisicao("POST", "sabia.exemplo.test:8080", "http://sabia.exemplo.test")).passou()).isFalse();
        assertThat(aplicar(semHttps, requisicao("POST", "localhost:8080", "http://localhost:9090")).passou()).isFalse();
        assertThat(aplicar(seguro, requisicao("POST", "localhost:8080", "http://localhost")).passou()).isFalse();
    }

    // ---- O loopback: é como se roda no notebook, por http://localhost, com Secure ligado ---------

    @ParameterizedTest
    @ValueSource(strings = {"localhost:8080", "localhost", "127.0.0.1:8080", "[::1]:8080", "LOCALHOST:8080"})
    void noLoopbackHttpEHttpsDoProprioHostPassamMesmoComSecureLigado(String host) throws Exception {
        assertThat(aplicar(seguro, requisicao("POST", host, "http://" + host)).passou()).as("http://" + host).isTrue();
        assertThat(aplicar(seguro, requisicao("POST", host, "https://" + host)).passou()).as("https://" + host).isTrue();
    }

    @Test
    void noLoopbackOutroSiteOuOutraPortaAindaEhBarrado() throws Exception {
        assertThat(aplicar(seguro, requisicao("POST", "localhost:8080", "http://site-malicioso.example")).passou()).isFalse();
        assertThat(aplicar(seguro, requisicao("POST", "localhost:8080", "http://127.0.0.1:8080")).passou())
                .as("outro nome do mesmo loopback é outra origem")
                .isFalse();
        assertThat(aplicar(seguro, requisicao("POST", "localhost:8080", "null")).passou()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost.site-malicioso.example", "localhostx", "xlocalhost", "127.0.0.10", "meu-localhost.dev.br"})
    void hostQueSoParecELoopbackNaoGanhaAExcecao(String host) throws Exception {
        assertThat(aplicar(seguro, requisicao("POST", host, "http://" + host)).passou()).isFalse();
        assertThat(aplicar(seguro, requisicao("POST", host, "https://" + host)).passou()).isTrue();
    }

    @Test
    void oHostNaoDistingueMaiusculaDeMinuscula() throws Exception {
        assertThat(aplicar(seguro, requisicao("POST", "AndreTeixeira.DEV.br", "https://andreteixeira.dev.br")).passou())
                .isTrue();
    }

    @Test
    void semHostComOriginNaoHaComoConferirEBarra() throws Exception {
        Resultado resultado = aplicar(seguro, requisicao("POST", null, "https://andreteixeira.dev.br"));

        assertThat(resultado.passou()).isFalse();
        assertThat(resultado.resposta().getStatus()).isEqualTo(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void osMetodosQueMudamEstadoSaoConferidos(String metodo) throws Exception {
        assertThat(aplicar(seguro, requisicao(metodo, "andreteixeira.dev.br", "https://outro.example")).passou()).isFalse();
        assertThat(aplicar(seguro, requisicao(metodo, "andreteixeira.dev.br", "https://andreteixeira.dev.br")).passou())
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void osMetodosQueNaoMudamEstadoNaoSaoConferidos(String metodo) throws Exception {
        assertThat(aplicar(seguro, requisicao(metodo, "andreteixeira.dev.br", "https://outro.example")).passou()).isTrue();
    }

    @Test
    void oOriginForjadoComQuebraDeLinhaNaoQuebraOFiltro() throws Exception {
        Resultado resultado = aplicar(
                seguro, requisicao("POST", "andreteixeira.dev.br", "https://x.example\r\nSet-Cookie: invasor=1"));

        assertThat(resultado.passou()).isFalse();
        assertThat(resultado.resposta().getStatus()).isEqualTo(403);
        assertThat(resultado.resposta().getHeader("Set-Cookie")).isNull();
    }
}
