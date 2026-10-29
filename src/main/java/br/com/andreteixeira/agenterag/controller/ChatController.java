package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioService;
import br.com.andreteixeira.agenterag.limite.LimitesProperties;
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
 * Os limites do chat público são aplicados aqui, nesta ordem: validação do
 * corpo, tamanho máximo da mensagem, limites diários e só então o grafo.
 * Requisição inválida, mensagem longa demais e pergunta recusada por limite
 * não contam como pergunta e não chegam ao modelo.
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
    public ConversaService.RespostaConversa chat(@Valid @RequestBody ChatRequest requisicao, HttpServletRequest http) {
        // IllegalArgumentException vira 400 com a mensagem original (ver GlobalExceptionHandler).
        // Vem antes da contagem: mensagem longa demais não é uma pergunta aceita.
        int maximo = limites.tamanhoMaximoMensagem();
        if (requisicao.mensagem().length() > maximo) {
            throw new IllegalArgumentException("A mensagem pode ter no máximo " + maximo + " caracteres.");
        }
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
