package br.com.andreteixeira.agenterag.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.andreteixeira.agenterag.grafo.ConversaService;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioService;
import br.com.andreteixeira.agenterag.limite.LimitesProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * O tamanho máximo da mensagem vem de {@code app.demo.limites.tamanho-maximo-mensagem},
 * não do código: com a propriedade em 20, o limite e o número da mensagem de erro
 * acompanham. O {@link ChatControllerTest} cobre o valor de produção (1000).
 */
@WebMvcTest(
        controllers = ChatController.class,
        properties = "app.demo.limites.tamanho-maximo-mensagem=20")
@EnableConfigurationProperties(LimitesProperties.class)
class ChatControllerTamanhoConfiguradoTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversaService conversaService;

    @MockitoBean
    private LimiteDiarioService limiteDiarioService;

    private static String corpoComMensagem(String mensagem) {
        return "{\"threadId\":\"t1\",\"mensagem\":\"" + mensagem + "\",\"empresaEscolhida\":\"bimbam\"}";
    }

    @Test
    void oLimiteEOTextoDoErroAcompanhamAConfiguracao() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComMensagem("a".repeat(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erro").value("A mensagem pode ter no máximo 20 caracteres."));

        verifyNoInteractions(limiteDiarioService, conversaService);
    }

    @Test
    void mensagemNoLimiteConfiguradoEhAceita() throws Exception {
        when(conversaService.responder(anyString(), any(), anyString()))
                .thenReturn(new ConversaService.RespostaConversa("resposta", List.of(), "bimbam"));

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoComMensagem("a".repeat(20))))
                .andExpect(status().isOk());
    }
}
