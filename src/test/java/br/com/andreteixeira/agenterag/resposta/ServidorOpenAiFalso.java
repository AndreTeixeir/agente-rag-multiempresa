package br.com.andreteixeira.agenterag.resposta;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servidor HTTP local (só o {@code com.sun.net.httpserver} do JDK, sem
 * dependência nova) que faz o papel da API compatível com a OpenAI da
 * Maritaca nos testes que não podem chamar o provedor de verdade. Escuta em
 * 127.0.0.1, porta escolhida pelo sistema, e guarda o que recebeu para o teste
 * conferir caminho, cabeçalho, corpo e número de requisições.
 * <p>
 * Um executor com várias threads atende cada requisição: um handler travado
 * (ver {@link #travarPor}) não impede o atendimento da nova tentativa.
 * <p>
 * Público porque é usado por testes de mais de um pacote.
 */
public final class ServidorOpenAiFalso implements AutoCloseable {

    private final HttpServer servidor;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger requisicoes = new AtomicInteger();
    private final List<String> caminhos = new CopyOnWriteArrayList<>();
    private final List<String> autorizacoes = new CopyOnWriteArrayList<>();
    private final List<String> corpos = new CopyOnWriteArrayList<>();
    private volatile Comportamento comportamento = exchange -> enviar(exchange, 200, respostaDeChat("ok"));

    public ServidorOpenAiFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        servidor.setExecutor(executor);
        servidor.createContext("/", this::atender);
        servidor.start();
    }

    /** Endereço base para {@code app.resposta.chat.base-url}, com o mesmo prefixo {@code /api} da Maritaca. */
    public String baseUrl() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/api";
    }

    public void responderSempre(int status, String corpoJson) {
        comportamento = exchange -> enviar(exchange, status, corpoJson);
    }

    /** Aceita a requisição e só responde depois de {@code espera} — tempo suficiente para o cliente estourar o limite. */
    public void travarPor(Duration espera) {
        comportamento = exchange -> {
            try {
                Thread.sleep(espera.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public int requisicoes() {
        return requisicoes.get();
    }

    public List<String> caminhos() {
        return caminhos;
    }

    public List<String> autorizacoes() {
        return autorizacoes;
    }

    public List<String> corpos() {
        return corpos;
    }

    /** Corpo mínimo de uma resposta de chat no formato da OpenAI. */
    public static String respostaDeChat(String texto) {
        return """
                {"id":"chatcmpl-teste","object":"chat.completion","created":1,"model":"sabiazinho-4",
                 "choices":[{"index":0,"message":{"role":"assistant","content":"%s"},"finish_reason":"stop"}],
                 "usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2}}
                """.formatted(texto);
    }

    /** Corpo de erro no formato da OpenAI. */
    public static String erroDoProvedor(String mensagem) {
        return """
                {"error":{"message":"%s","type":"erro_de_teste"}}
                """.formatted(mensagem);
    }

    private void atender(HttpExchange exchange) throws IOException {
        try {
            requisicoes.incrementAndGet();
            caminhos.add(exchange.getRequestURI().getPath());
            autorizacoes.add(exchange.getRequestHeaders().getFirst("Authorization"));
            corpos.add(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
            comportamento.responder(exchange);
        } finally {
            exchange.close();
        }
    }

    private static void enviar(HttpExchange exchange, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream saida = exchange.getResponseBody()) {
            saida.write(bytes);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
        executor.shutdownNow();
    }

    @FunctionalInterface
    private interface Comportamento {

        void responder(HttpExchange exchange) throws IOException;
    }
}
