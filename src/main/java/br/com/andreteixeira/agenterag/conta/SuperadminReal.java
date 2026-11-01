package br.com.andreteixeira.agenterag.conta;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * O superadmin real: a conta do dono da aplicação, criada (ou atualizada) no boot a partir de
 * {@code APP_SUPERADMIN_USUARIO} e {@code APP_SUPERADMIN_SENHA}. Não vem de migração, para a senha
 * nunca estar no repositório, e nunca aparece em listagem nem na interface.
 * <p>
 * No boot:
 * <ul>
 *     <li>as duas variáveis presentes e válidas: cria a conta, ou — se já existe — atualiza o
 *     usuário e, se a senha mudou, o hash. Log {@code INFO} sem nome nem senha;</li>
 *     <li>as duas ausentes: nada é criado; log {@code INFO} dizendo que não está configurado;</li>
 *     <li>uma só, ou valores inválidos (senha curta demais, usuário fora do padrão, usuário que já é
 *     de outra conta): log {@code ERROR}, sem os valores, e a aplicação segue sem superadmin real.</li>
 * </ul>
 * Sem uma configuração válida, {@link #ativo()} é falso e a linha que já exista no banco (de um boot
 * anterior) não consegue entrar: o login a recusa e o token dela deixa de valer. Não é apagada —
 * apagar tiraria a proteção das contas que ela criou.
 */
@Component
@EnableConfigurationProperties(SuperadminProperties.class)
@Order(1)
public class SuperadminReal implements ApplicationRunner {

    static final Pattern USUARIO_VALIDO = Pattern.compile("^[a-z0-9._-]{3,40}$");
    static final int TAMANHO_MINIMO_DA_SENHA = 8;
    /** O BCrypt só considera os 72 primeiros bytes da senha. */
    static final int TAMANHO_MAXIMO_DA_SENHA_EM_BYTES = 72;

    private static final Logger log = LoggerFactory.getLogger(SuperadminReal.class);

    private final SuperadminProperties variaveis;
    private final ContaRepository contas;
    private final PasswordEncoder encoder;
    private volatile boolean ativo;

    public SuperadminReal(SuperadminProperties variaveis, ContaRepository contas, PasswordEncoder encoder) {
        this.variaveis = variaveis;
        this.contas = contas;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        configurar();
    }

    /** O superadmin real está configurado e pode entrar? */
    public boolean ativo() {
        return ativo;
    }

    public void configurar() {
        ativo = false;
        boolean semUsuario = variaveis.usuario().isBlank();
        boolean semSenha = variaveis.senha().isBlank();
        if (semUsuario && semSenha) {
            log.info("Superadmin real não configurado: APP_SUPERADMIN_USUARIO e APP_SUPERADMIN_SENHA ausentes.");
            return;
        }
        if (semUsuario || semSenha) {
            log.error("Superadmin real NÃO configurado: APP_SUPERADMIN_USUARIO e APP_SUPERADMIN_SENHA precisam estar definidas juntas.");
            return;
        }

        String usuario = AutenticacaoService.normalizar(variaveis.usuario());
        String senha = variaveis.senha();
        if (!USUARIO_VALIDO.matcher(usuario).matches()) {
            log.error("Superadmin real NÃO configurado: APP_SUPERADMIN_USUARIO precisa ter de 3 a 40 caracteres entre letras, números, ponto, hífen e sublinhado.");
            return;
        }
        if (senha.length() < TAMANHO_MINIMO_DA_SENHA
                || senha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_DA_SENHA_EM_BYTES) {
            log.error("Superadmin real NÃO configurado: APP_SUPERADMIN_SENHA precisa ter de {} caracteres a {} bytes.",
                    TAMANHO_MINIMO_DA_SENHA, TAMANHO_MAXIMO_DA_SENHA_EM_BYTES);
            return;
        }

        try {
            Optional<Conta> existente = contas.buscarSuperadminReal();
            if (existente.isEmpty()) {
                contas.inserir(usuario, encoder.encode(senha), Papel.SUPERADMIN, null, false, true, null);
            } else {
                Conta conta = existente.get();
                String hash = encoder.matches(senha, conta.senhaHash()) ? conta.senhaHash() : encoder.encode(senha);
                if (!conta.usuario().equals(usuario) || !hash.equals(conta.senhaHash())) {
                    contas.atualizarSuperadminReal(conta.id(), usuario, hash);
                }
            }
        } catch (DuplicateKeyException e) {
            log.error("Superadmin real NÃO configurado: o usuário de APP_SUPERADMIN_USUARIO já pertence a outra conta.");
            return;
        }

        ativo = true;
        log.info("Superadmin real configurado a partir das variáveis de ambiente.");
    }
}
