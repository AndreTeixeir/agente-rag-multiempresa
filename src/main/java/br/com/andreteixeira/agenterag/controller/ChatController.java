package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
 * Os limites diários do chat público são aplicados aqui, DEPOIS da validação
 * do corpo e ANTES de chamar o grafo: requisição inválida não conta, e
 * pergunta recusada por limite também não conta nem chega ao modelo.
 */
@RestController
public class ChatController {

    private final ConversaService conversaService;
    private final LimiteDiarioService limiteDiarioService;

    public ChatController(ConversaService conversaService, LimiteDiarioService limiteDiarioService) {
        this.conversaService = conversaService;
        this.limiteDiarioService = limiteDiarioService;
    }

    /**
     * {@code HttpServletRequest#getRemoteAddr()} já é o IP do cliente real: com
     * {@code server.forward-headers-strategy=native}, o Tomcat troca o
     * endereço da conexão pelo do {@code X-Forwarded-For} quando ela vem de um
     * proxy interno (ver {@code application.yml}). Este código não lê o
     * cabeçalho.
     */
    @PostMapping("/api/chat")
    public ConversaService.RespostaConversa chat(@Valid @RequestBody ChatRequest requisicao, HttpServletRequest http) {
        limiteDiarioService.registrarPergunta(http.getRemoteAddr());
        return conversaService.responder(requisicao.threadId(), requisicao.empresaEscolhida(), requisicao.mensagem());
    }

    /**
     * {@code empresaEscolhida} sem {@code @NotBlank} de propósito — é
     * opcional a partir do 2º turno (ver javadoc de
     * {@link ConversaService#responder}).
     */
    public record ChatRequest(@NotBlank String threadId, @NotBlank String mensagem, String empresaEscolhida) {
    }
}
