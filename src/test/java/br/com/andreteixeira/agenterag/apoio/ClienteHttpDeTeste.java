package br.com.andreteixeira.agenterag.apoio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.Map;

/**
 * Um cliente HTTP mínimo para os testes de integração contra o Tomcat real: manda o cookie da sessão
 * e o IP do cliente (por {@code X-Forwarded-For}, que a aplicação aceita do loopback) e devolve a
 * resposta como veio. Sem seguir redirecionamentos, sem guardar cookies.
 */
public final class ClienteHttpDeTeste {

    public static final ObjectMapper JSON = new ObjectMapper();

    private final HttpClient http = HttpClient.newHttpClient();
    private final int porta;

    public ClienteHttpDeTeste(int porta) {
        this.porta = porta;
    }

    public HttpResponse<String> enviar(String metodo, String caminho, String corpoJson, String cookie, String ip) {
        HttpRequest.Builder requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + caminho))
                .method(metodo, corpoJson == null ? BodyPublishers.noBody() : BodyPublishers.ofString(corpoJson));
        if (corpoJson != null) {
            requisicao.header("Content-Type", "application/json");
        }
        if (cookie != null) {
            requisicao.header("Cookie", cookie);
        }
        if (ip != null) {
            requisicao.header("X-Forwarded-For", ip);
        }
        try {
            return http.send(requisicao.build(), BodyHandlers.ofString());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    public HttpResponse<String> get(String caminho, String cookie, String ip) {
        return enviar("GET", caminho, null, cookie, ip);
    }

    public HttpResponse<String> post(String caminho, Map<String, ?> corpo, String cookie, String ip) {
        return enviar("POST", caminho, escrever(corpo), cookie, ip);
    }

    public HttpResponse<String> delete(String caminho, String cookie, String ip) {
        return enviar("DELETE", caminho, null, cookie, ip);
    }

    public static String escrever(Map<String, ?> corpo) {
        try {
            return JSON.writeValueAsString(corpo);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static JsonNode ler(HttpResponse<String> resposta) {
        try {
            return JSON.readTree(resposta.body());
        } catch (IOException e) {
            throw new IllegalStateException("corpo que não é JSON: " + resposta.body(), e);
        }
    }

    /** A mensagem do corpo {@code {"erro": "..."}}. */
    public static String erro(HttpResponse<String> resposta) {
        return ler(resposta).path("erro").asText();
    }
}
