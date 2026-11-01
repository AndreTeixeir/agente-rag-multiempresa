package br.com.andreteixeira.agenterag.conta;

import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Confere usuário e senha e devolve a conta. Não cuida de limites nem de token: isso é do
 * {@code AuthController}.
 * <p>
 * Usuário inexistente também roda uma conferência de BCrypt (contra um hash que ninguém tem), para
 * a resposta demorar o mesmo que a de uma senha errada e o tempo não revelar se o usuário existe.
 */
@Service
public class AutenticacaoService {

    private final ContaRepository contas;
    private final PasswordEncoder encoder;
    private final SuperadminReal superadminReal;
    /** Hash de uma senha que ninguém usa — só para gastar o mesmo tempo quando o usuário não existe. */
    private final String hashQueNinguemTem;

    public AutenticacaoService(ContaRepository contas, PasswordEncoder encoder, SuperadminReal superadminReal) {
        this.contas = contas;
        this.encoder = encoder;
        this.superadminReal = superadminReal;
        this.hashQueNinguemTem = encoder.encode("senha-que-ninguem-tem-" + System.nanoTime());
    }

    /**
     * A conta, se o usuário existe e a senha confere; vazio nos dois outros casos, sem distinguir. A
     * linha do superadmin real conta como inexistente enquanto ele não estiver configurado.
     */
    public Optional<Conta> autenticar(String usuarioInformado, String senhaInformada) {
        Optional<Conta> conta = contas.buscarPorUsuario(normalizar(usuarioInformado)).filter(this::utilizavel);
        String hash = conta.map(Conta::senhaHash).orElse(hashQueNinguemTem);
        boolean confere = confere(senhaInformada, hash);
        return conta.filter(c -> confere);
    }

    /**
     * A conta do id que o token da sessão traz — vazio se ela já não existe (excluída, ou apagada no
     * reset) ou se é o superadmin real e ele não está configurado neste boot.
     */
    public Optional<Conta> contaDaSessao(long id) {
        return contas.buscarPorId(id).filter(this::utilizavel);
    }

    private boolean utilizavel(Conta conta) {
        return !conta.superadminReal() || superadminReal.ativo();
    }

    static String normalizar(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase(Locale.ROOT);
    }

    private boolean confere(String senha, String hash) {
        try {
            return encoder.matches(senha, hash);
        } catch (IllegalArgumentException e) {
            // Senha que o BCrypt não aceita (por exemplo, mais de 72 bytes): nunca confere.
            return false;
        }
    }
}
