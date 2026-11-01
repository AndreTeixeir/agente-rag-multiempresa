package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.conta.Papel;
import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioService;
import br.com.andreteixeira.agenterag.limite.LimitesProperties;
import br.com.andreteixeira.agenterag.seguranca.ContaAutenticada;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Etapa 6 — ponto de entrada HTTP do grafo. Traduz a requisição para a
 * assinatura real de {@link ConversaService#responder}; validação de
 * {@code empresaEscolhida} continua em {@code ConversaNodes} (só ele sabe, pelo
 * estado da sessão, se está no 1º turno ou não — ver discussão registrada no
 * relatório da Etapa 6).
 * <p>
 * Exige login. A empresa vem da CONTA para USUARIO e ADMIN — o que o corpo mandar em
 * {@code empresaEscolhida} é ignorado — e do corpo só para SUPERADMIN, que conversa com qualquer
 * empresa. A conversa é presa à conta ({@link br.com.andreteixeira.agenterag.grafo.ChaveDaConversa}):
 * o mesmo {@code threadId} em duas contas são duas conversas.
 * <p>
 * Os limites do chat público são aplicados aqui, nesta ordem: validação do
 * corpo, tamanho máximo da mensagem, limites diários e só então o grafo.
 * Requisição inválida, mensagem longa demais e pergunta recusada por limite
 * não contam como pergunta e não chegam ao modelo. O superadmin real fica fora dos limites.
 */
@RestController
public class ChatController {

    private final ConversaService conversaService;
    private final LimiteDiarioService limiteDiarioService;
    private final LimitesProperties limites;

    public ChatController(
            ConversaService conversaService, LimiteDiarioService limiteDiarioService, LimitesProperties limites) {
        this.conversaService = conversaService;
        this.limiteDiarioService = limiteDiarioService;
        this.limites = limites;
    }

    /**
     * {@code HttpServletRequest#getRemoteAddr()} já é o IP do cliente real: com
     * {@code server.forward-headers-strategy=native}, o Tomcat troca o
     * endereço da conexão pelo do {@code X-Forwarded-For} quando ela vem de um
     * proxy interno (ver {@code application.yml}). Este código não lê o
     * cabeçalho.
     */
    @PostMapping("/api/chat")
    public ConversaService.RespostaConversa chat(
            @Valid @RequestBody ChatRequest requisicao,
            @AuthenticationPrincipal ContaAutenticada conta,
            HttpServletRequest http) {
        // IllegalArgumentException vira 400 com a mensagem original (ver GlobalExceptionHandler).
        // Vem antes da contagem: mensagem longa demais não é uma pergunta aceita.
        int maximo = limites.tamanhoMaximoMensagem();
        if (requisicao.mensagem().length() > maximo) {
            throw new IllegalArgumentException("A mensagem pode ter no máximo " + maximo + " caracteres.");
        }
        if (!conta.superadminReal()) {
            limiteDiarioService.registrarPergunta(http.getRemoteAddr());
        }
        String empresa = conta.papel() == Papel.SUPERADMIN ? requisicao.empresaEscolhida() : conta.empresa();
        return conversaService.responder(conta.id(), requisicao.threadId(), empresa, requisicao.mensagem());
    }

    /**
     * {@code empresaEscolhida} sem {@code @NotBlank} de propósito — é
     * opcional a partir do 2º turno (ver javadoc de
     * {@link ConversaService#responder}), e só o SUPERADMIN a usa. O {@code threadId} é o UUID do
     * navegador, mas aceita qualquer texto de 1 a 64 letras, números, hífen e sublinhado: ele vira
     * parte de {@code LG4JThread.thread_name}, que tem 255 caracteres.
     */
    public record ChatRequest(
            @NotBlank
                    @Pattern(
                            regexp = "^[A-Za-z0-9_-]{1,64}$",
                            message = "threadId inválido: use de 1 a 64 letras, números, hífen ou sublinhado.")
                    String threadId,
            @NotBlank String mensagem,
            String empresaEscolhida) {
    }
}
