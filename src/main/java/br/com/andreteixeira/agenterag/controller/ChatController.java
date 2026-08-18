package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Etapa 6 — ponto de entrada HTTP do grafo. Só traduz a requisição para a
 * assinatura real de {@link ConversaService#responder}, sem lógica própria;
 * validação de {@code empresaEscolhida} continua em {@code ConversaNodes} (só
 * ele sabe, pelo estado da sessão, se está no 1º turno ou não — ver
 * discussão registrada no relatório da Etapa 6).
 */
@RestController
public class ChatController {

    private final ConversaService conversaService;

    public ChatController(ConversaService conversaService) {
        this.conversaService = conversaService;
    }

    @PostMapping("/api/chat")
    public ConversaService.RespostaConversa chat(@Valid @RequestBody ChatRequest requisicao) {
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
