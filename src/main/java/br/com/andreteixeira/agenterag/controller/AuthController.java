package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.conta.AutenticacaoService;
import br.com.andreteixeira.agenterag.conta.Conta;
import br.com.andreteixeira.agenterag.conta.CredenciaisInvalidasException;
import br.com.andreteixeira.agenterag.conta.Papel;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioService;
import br.com.andreteixeira.agenterag.seguranca.ContaAutenticada;
import br.com.andreteixeira.agenterag.seguranca.SessaoCookie;
import br.com.andreteixeira.agenterag.seguranca.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login, logout e "quem sou eu". O login confere usuário e senha e, se certos, devolve a conta e abre
 * a sessão num cookie ({@link SessaoCookie}); o corpo nunca leva o token.
 * <p>
 * Tentativas de login: no máximo 10 erradas por IP por hora ({@code app.demo.limites}). A vaga é
 * reservada antes de a senha ser conferida e devolvida se ela estiver certa — por isso 10 tentativas
 * em paralelo não dão 10 palpites a mais — e a mensagem de erro é a mesma para usuário inexistente e
 * senha errada. {@code getRemoteAddr()} já é o IP do cliente real (ver {@link ChatController}).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AutenticacaoService autenticacao;
    private final LimiteDiarioService limites;
    private final TokenService tokens;
    private final SessaoCookie cookie;

    public AuthController(
            AutenticacaoService autenticacao, LimiteDiarioService limites, TokenService tokens, SessaoCookie cookie) {
        this.autenticacao = autenticacao;
        this.limites = limites;
        this.tokens = tokens;
        this.cookie = cookie;
    }

    @PostMapping("/login")
    public ResponseEntity<SessaoResponse> login(@Valid @RequestBody LoginRequest requisicao, HttpServletRequest http) {
        LimiteDiarioService.TentativaDeLogin vaga = limites.reservarTentativaDeLogin(http.getRemoteAddr());
        Conta conta = autenticacao
                .autenticar(requisicao.usuario(), requisicao.senha())
                .orElseThrow(CredenciaisInvalidasException::new);
        limites.devolverTentativaDeLogin(vaga);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.criar(tokens.emitir(conta.id())))
                .body(SessaoResponse.de(ContaAutenticada.de(conta)));
    }

    /** Apaga o cookie. Público e idempotente: sair sem estar logado (ou com a sessão vencida) dá certo também. */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.apagar())
                .body(Map.of("mensagem", "Sessão encerrada."));
    }

    @GetMapping("/eu")
    public SessaoResponse eu(@AuthenticationPrincipal ContaAutenticada conta) {
        return SessaoResponse.de(conta);
    }

    public record LoginRequest(
            @NotBlank(message = "Informe o usuário.") @Size(max = 100, message = "Usuário ou senha inválidos.") String usuario,
            @NotBlank(message = "Informe a senha.") @Size(max = 200, message = "Usuário ou senha inválidos.") String senha) {
    }

    /** O que a interface precisa da conta; o usuário do superadmin real vem mascarado (ver {@link ContaAutenticada#nomeExibido}). */
    public record SessaoResponse(String usuario, Papel papel, String empresa) {

        static SessaoResponse de(ContaAutenticada conta) {
            return new SessaoResponse(conta.nomeExibido(), conta.papel(), conta.empresa());
        }
    }
}
