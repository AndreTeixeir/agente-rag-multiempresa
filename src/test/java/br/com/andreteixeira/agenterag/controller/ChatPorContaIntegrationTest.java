package br.com.andreteixeira.agenterag.controller;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.apoio.PostgresDeTeste;
import br.com.andreteixeira.agenterag.apoio.SessaoDeTeste;
import br.com.andreteixeira.agenterag.conta.ContaRepository;
import br.com.andreteixeira.agenterag.resposta.ServidorOpenAiFalso;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A conversa presa à conta e a empresa pela conta, contra o grafo REAL (com o checkpoint no
 * Postgres), um Tomcat real e as contas semeadas. Só o provedor do modelo é falso
 * ({@link ServidorOpenAiFalso}) e o embedding e o vector store são os dublês de
 * {@link DublesDoGrafoConfig}: zero chamadas ao Gemini e à Maritaca.
 * <p>
 * A prova de que uma conta não alcança a conversa de outra está na contagem de chamadas ao modelo: a
 * segunda pergunta de uma conversa faz DUAS (reescrita da consulta + resposta), a primeira faz UMA.
 * Se a conta B, com o mesmo {@code threadId} da A, enxergasse o histórico dela, a primeira pergunta
 * dela faria duas.
 */
@Import(DublesDoGrafoConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ChatPorContaIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ServidorOpenAiFalso provedor = criarProvedor();

    private static ServidorOpenAiFalso criarProvedor() {
        try {
            return new ServidorOpenAiFalso();
        } catch (IOException e) {
            throw new IllegalStateException("não subiu o servidor HTTP local do teste", e);
        }
    }

    @DynamicPropertySource
    static void propriedadesDinamicas(DynamicPropertyRegistry registry) {
        PostgresDeTeste.registrar(registry);
        registry.add("app.resposta.chat.base-url", provedor::baseUrl);
        registry.add("app.demo.limites.perguntas-por-dia", () -> "1000");
        registry.add("app.demo.limites.perguntas-por-ip-por-dia", () -> "1000");
    }

    @Autowired
    private TokenService tokens;

    @Autowired
    private ContaRepository contas;

    @Autowired
    private JdbcClient jdbc;

    @LocalServerPort
    private int porta;

    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void provedorResponde() {
        provedor.responderSempre(200, ServidorOpenAiFalso.respostaDeChat("resposta de teste"));
    }

    @AfterAll
    static void fecharProvedor() {
        provedor.close();
    }

    // ---- Conversa presa à conta ----------------------------------------------------------

    @Test
    void duasContasComOMesmoThreadIdTemConversasSeparadas() throws Exception {
        String thread = UUID.randomUUID().toString();
        String contaA = sessaoDe("usuario.bimbam");
        String contaB = sessaoDe("usuario.mercadocentral");

        int antes = provedor.requisicoes();
        assertThat(postarChat(contaA, thread, null, "primeira da A").statusCode()).isEqualTo(200);
        assertThat(provedor.requisicoes() - antes).as("1ª pergunta da A: só a resposta").isEqualTo(1);

        antes = provedor.requisicoes();
        HttpResponse<String> primeiraDaB = postarChat(contaB, thread, null, "primeira da B");
        assertThat(primeiraDaB.statusCode()).isEqualTo(200);
        assertThat(provedor.requisicoes() - antes)
                .as("a B tem o mesmo threadId da A, mas não vê o histórico dela: sem reescrita, só a resposta")
                .isEqualTo(1);
        assertThat(JSON.readTree(primeiraDaB.body()).get("empresa").asText()).isEqualTo("mercado-central");

        antes = provedor.requisicoes();
        postarChat(contaA, thread, null, "segunda da A");
        assertThat(provedor.requisicoes() - antes).as("2ª da A: reescrita + resposta").isEqualTo(2);

        assertThat(nomesDasConversas(thread))
                .containsExactlyInAnyOrder(
                        idDe("usuario.bimbam") + ":" + thread, idDe("usuario.mercadocentral") + ":" + thread);
    }

    @Test
    void oThreadIdPuroDoNavegadorNuncaViraNomeDeConversa() throws Exception {
        String thread = UUID.randomUUID().toString();

        postarChat(sessaoDe("admin.santopegasus"), thread, null, "pergunta");

        assertThat(jdbc.sql("SELECT COUNT(*) FROM LG4JThread WHERE thread_name = :nome")
                        .param("nome", thread)
                        .query(Integer.class)
                        .single())
                .isZero();
        assertThat(nomesDasConversas(thread)).containsExactly(idDe("admin.santopegasus") + ":" + thread);
    }

    @Test
    void aMesmaContaContinuaASuaConversaDeUmaRequisicaoParaOutra() throws Exception {
        String thread = UUID.randomUUID().toString();
        String sessao = sessaoDe("usuario.santopegasus");

        postarChat(sessao, thread, null, "primeira");
        int antes = provedor.requisicoes();
        postarChat(sessao, thread, null, "segunda");

        assertThat(provedor.requisicoes() - antes).as("tem histórico: reescrita + resposta").isEqualTo(2);
    }

    // ---- Empresa pela conta --------------------------------------------------------------

    @Test
    void usuarioQueMandaOutraEmpresaNoCorpoContinuaNaDaConta() throws Exception {
        HttpResponse<String> resposta =
                postarChat(sessaoDe("usuario.bimbam"), UUID.randomUUID().toString(), "mercado-central", "pergunta");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(resposta.body()).get("empresa").asText()).isEqualTo("bimbam");
    }

    @Test
    void adminQueMandaOutraEmpresaNoCorpoContinuaNaDaConta() throws Exception {
        HttpResponse<String> resposta =
                postarChat(sessaoDe("admin.mercadocentral"), UUID.randomUUID().toString(), "bimbam", "pergunta");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(resposta.body()).get("empresa").asText()).isEqualTo("mercado-central");
    }

    @Test
    void usuarioNaoPrecisaMandarEmpresaNenhuma() throws Exception {
        HttpResponse<String> resposta =
                postarChat(sessaoDe("usuario.santopegasus"), UUID.randomUUID().toString(), null, "pergunta");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(resposta.body()).get("empresa").asText()).isEqualTo("santo-pegasus");
    }

    @Test
    void empresaInexistenteNoCorpoDeUmUsuarioEhIgnoradaEntaoNaoHa400() throws Exception {
        HttpResponse<String> resposta =
                postarChat(sessaoDe("usuario.bimbam"), UUID.randomUUID().toString(), "empresa-que-nao-existe", "pergunta");

        assertThat(resposta.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(resposta.body()).get("empresa").asText()).isEqualTo("bimbam");
    }

    @Test
    void superadminEscolheAEmpresaPeloCorpoEElaFicaFixaNaConversa() throws Exception {
        String thread = UUID.randomUUID().toString();
        String sessao = sessaoDe("superadmin.demo");

        HttpResponse<String> primeira = postarChat(sessao, thread, "santo-pegasus", "primeira");
        assertThat(JSON.readTree(primeira.body()).get("empresa").asText()).isEqualTo("santo-pegasus");

        HttpResponse<String> semEmpresa = postarChat(sessao, thread, null, "segunda");
        assertThat(JSON.readTree(semEmpresa.body()).get("empresa").asText()).isEqualTo("santo-pegasus");

        HttpResponse<String> tentandoTrocar = postarChat(sessao, thread, "bimbam", "terceira");
        assertThat(JSON.readTree(tentandoTrocar.body()).get("empresa").asText())
                .as("trocar de empresa no meio da conversa continua sendo ignorado")
                .isEqualTo("santo-pegasus");
    }

    @Test
    void superadminSemEmpresaNaPrimeiraMensagemRecebe400() throws Exception {
        HttpResponse<String> resposta = postarChat(sessaoDe("superadmin.demo"), UUID.randomUUID().toString(), null, "pergunta");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(resposta.body()).get("erro").asText()).contains("precisa informar a empresa");
    }

    @Test
    void superadminComEmpresaInexistenteRecebe400() throws Exception {
        HttpResponse<String> resposta =
                postarChat(sessaoDe("superadmin.demo"), UUID.randomUUID().toString(), "empresa-que-nao-existe", "pergunta");

        assertThat(resposta.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(resposta.body()).get("erro").asText()).contains("não reconhecida");
    }

    // ---- threadId ------------------------------------------------------------------------

    @Test
    void threadIdForaDoPadraoRecebe400() throws Exception {
        for (String ruim : List.of("com espaco", "../outra", "1:t1", "a".repeat(65))) {
            HttpResponse<String> resposta = postarChat(sessaoDe("usuario.bimbam"), ruim, null, "pergunta");
            assertThat(resposta.statusCode()).as(ruim).isEqualTo(400);
        }
    }

    // ---- apoio ---------------------------------------------------------------------------

    private long idDe(String usuario) {
        return contas.buscarPorUsuario(usuario).orElseThrow().id();
    }

    private String sessaoDe(String usuario) {
        return SessaoDeTeste.NOME_DO_COOKIE + "=" + tokens.emitir(idDe(usuario));
    }

    private List<String> nomesDasConversas(String thread) {
        return jdbc.sql("SELECT thread_name FROM LG4JThread WHERE thread_name LIKE :padrao")
                .param("padrao", "%" + thread)
                .query(String.class)
                .list();
    }

    private HttpResponse<String> postarChat(String cookie, String thread, String empresa, String mensagem) throws Exception {
        Map<String, String> corpo = new HashMap<>();
        corpo.put("threadId", thread);
        corpo.put("mensagem", mensagem);
        if (empresa != null) {
            corpo.put("empresaEscolhida", empresa);
        }
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/chat"))
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .POST(BodyPublishers.ofString(JSON.writeValueAsString(corpo)))
                .build();
        return http.send(requisicao, BodyHandlers.ofString());
    }
}
