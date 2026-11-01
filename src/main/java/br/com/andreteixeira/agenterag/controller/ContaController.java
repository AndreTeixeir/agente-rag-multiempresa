package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.conta.ContaAutenticada;
import br.com.andreteixeira.agenterag.conta.ContaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administração de contas. Só ADMIN e SUPERADMIN chegam aqui (regra por rota, em
 * {@code SegurancaConfig}); o que cada um pode fazer com cada conta é do {@link ContaService}. O
 * caminho é {@code /api/contas}, sem prefixo em comum com o {@code /api/admin/ingest} do perfil dev.
 */
@RestController
@RequestMapping("/api/contas")
public class ContaController {

    private final ContaService contas;

    public ContaController(ContaService contas) {
        this.contas = contas;
    }

    @GetMapping
    public List<ContaService.ContaListada> listar(@AuthenticationPrincipal ContaAutenticada ator) {
        return contas.listar(ator);
    }

    /**
     * 201 com a conta criada. {@code getRemoteAddr()} já é o IP do cliente real (ver
     * {@link ChatController}), e é por ele que o teto de cadastros por IP conta.
     */
    @PostMapping
    public ResponseEntity<ContaService.ContaListada> criar(
            @Valid @RequestBody CriarContaRequest requisicao,
            @AuthenticationPrincipal ContaAutenticada ator,
            HttpServletRequest http) {
        ContaService.ContaListada criada = contas.criar(
                ator, requisicao.usuario(), requisicao.senha(), requisicao.papel(), requisicao.empresa(), http.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(criada);
    }

    @DeleteMapping("/{id}")
    public Map<String, String> excluir(@PathVariable long id, @AuthenticationPrincipal ContaAutenticada ator) {
        contas.excluir(ator, id);
        return Map.of("mensagem", "Conta excluída.");
    }

    /**
     * {@code papel} e {@code empresa} só valem para o SUPERADMIN; para o ADMIN o servidor força
     * USUARIO e a empresa dele, e o que vier aqui é ignorado.
     */
    public record CriarContaRequest(
            @NotBlank(message = "Informe o usuário.") @Size(max = 100, message = "Usuário inválido.") String usuario,
            @NotBlank(message = "Informe a senha.") @Size(max = 200, message = "A senha pode ter no máximo 72 bytes.") String senha,
            String papel,
            String empresa) {
    }
}
