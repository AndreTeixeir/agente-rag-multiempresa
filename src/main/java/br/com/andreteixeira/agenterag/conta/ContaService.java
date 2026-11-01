package br.com.andreteixeira.agenterag.conta;

import br.com.andreteixeira.agenterag.grafo.ConversaRepository;
import br.com.andreteixeira.agenterag.limite.LimiteDiarioService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Administração de contas: listar, criar e excluir. Quem pode o quê:
 * <ul>
 *     <li><b>ADMIN</b>: lista, cria e exclui usuários comuns da PRÓPRIA empresa. O servidor força o
 *     papel (USUARIO) e a empresa (a dele) ao criar — o que o corpo mandar sobre isso é ignorado. Não
 *     cria nem exclui administradores.</li>
 *     <li><b>SUPERADMIN de demonstração</b>: lista todas as contas (menos o superadmin real), cria
 *     administradores e usuários em qualquer empresa e exclui contas que não sejam protegidas — pode
 *     haver vários administradores por empresa.</li>
 *     <li><b>SUPERADMIN real</b>: o mesmo, sem a trava das contas criadas por ele (protegidas das
 *     demais) e fora do teto de cadastros.</li>
 * </ul>
 * Ninguém exclui as contas semeadas (fazem parte da demo e o reset não as recria), nem o superadmin
 * real, nem a si mesmo. Só o SUPERADMIN exclui administradores.
 * <p>
 * Recusas: 404 quando a conta alvo não é visível a quem pediu (outra empresa, o superadmin real),
 * 403 quando é visível mas não pode, 400 para erro de dado. Ver {@code GlobalExceptionHandler}.
 */
@Service
public class ContaService {

    static final int TAMANHO_MINIMO_DA_SENHA = SuperadminReal.TAMANHO_MINIMO_DA_SENHA;
    static final int TAMANHO_MAXIMO_DA_SENHA_EM_BYTES = SuperadminReal.TAMANHO_MAXIMO_DA_SENHA_EM_BYTES;

    private static final Logger log = LoggerFactory.getLogger(ContaService.class);

    private final ContaRepository contas;
    private final ConversaRepository conversas;
    private final PasswordEncoder encoder;
    private final LimiteDiarioService limites;
    private final TransactionOperations transacao;

    public ContaService(
            ContaRepository contas,
            ConversaRepository conversas,
            PasswordEncoder encoder,
            LimiteDiarioService limites,
            TransactionOperations transacao) {
        this.contas = contas;
        this.conversas = conversas;
        this.encoder = encoder;
        this.limites = limites;
        this.transacao = transacao;
    }

    /** O que a interface precisa de cada conta da listagem. */
    public record ContaListada(
            long id, String usuario, Papel papel, String empresa, boolean protegida, boolean podeExcluir, Instant criadaEm) {
    }

    public List<ContaListada> listar(ContaAutenticada ator) {
        List<Conta> visiveis =
                switch (ator.papel()) {
                    case ADMIN -> contas.listarUsuariosDaEmpresa(ator.empresa());
                    case SUPERADMIN -> contas.listarTodasExcetoReal();
                    case USUARIO -> throw new OperacaoNaoPermitidaException("Você não tem permissão para esta ação.");
                };
        return visiveis.stream().map(conta -> listada(ator, conta)).toList();
    }

    /**
     * Cria uma conta e devolve como ela aparece na listagem. As validações vêm primeiro (nenhuma gasta
     * vaga do teto de cadastros); a vaga e a inserção ficam na mesma transação, então um cadastro que
     * falha não a consome.
     */
    public ContaListada criar(
            ContaAutenticada ator, String usuarioInformado, String senha, String papelInformado, String empresaInformada, String ip) {
        Papel papel;
        String empresa;
        switch (ator.papel()) {
            case ADMIN -> {
                papel = Papel.USUARIO;
                empresa = ator.empresa();
            }
            case SUPERADMIN -> {
                papel = papelDeNovaConta(papelInformado);
                if (!Empresas.ehValida(empresaInformada)) {
                    throw new IllegalArgumentException("Empresa inválida. Valores aceitos: " + Empresas.SLUGS);
                }
                empresa = empresaInformada;
            }
            default -> throw new OperacaoNaoPermitidaException("Você não tem permissão para esta ação.");
        }

        String usuario = AutenticacaoService.normalizar(usuarioInformado);
        if (!SuperadminReal.USUARIO_VALIDO.matcher(usuario).matches()) {
            throw new IllegalArgumentException(
                    "Usuário inválido: use de 3 a 40 caracteres entre letras, números, ponto, hífen e sublinhado.");
        }
        validarSenha(senha);
        if (contas.buscarPorUsuario(usuario).isPresent()) {
            throw new IllegalArgumentException(USUARIO_EM_USO);
        }

        String hash = encoder.encode(senha);
        Long id;
        try {
            id = transacao.execute(status -> {
                if (!ator.superadminReal()) {
                    limites.registrarCadastro(ip);
                }
                return contas.inserir(usuario, hash, papel, empresa, false, false, ator.id());
            });
        } catch (DuplicateKeyException e) {
            // Outra requisição criou o mesmo usuário entre a conferência acima e a inserção.
            throw new IllegalArgumentException(USUARIO_EM_USO);
        }
        log.info("Conta criada: id={}, papel={}, empresa={}, por id={}", id, papel, empresa, ator.id());
        return listada(ator, contas.buscarPorId(Objects.requireNonNull(id)).orElseThrow());
    }

    public void excluir(ContaAutenticada ator, long alvoId) {
        Conta alvo = contas.buscarPorId(alvoId).orElseThrow(() -> new RecursoNaoEncontradoException(CONTA_NAO_ENCONTRADA));
        switch (avaliarExclusao(ator, alvo)) {
            case PODE -> transacao.executeWithoutResult(status -> {
                contas.excluir(alvo.id());
                conversas.apagarDaConta(alvo.id());
            });
            case NAO_VISIVEL -> throw new RecursoNaoEncontradoException(CONTA_NAO_ENCONTRADA);
            case PROPRIA_CONTA -> throw new IllegalArgumentException("Você não pode excluir a própria conta.");
            case PROTEGIDA -> throw new OperacaoNaoPermitidaException("Esta conta é protegida e não pode ser excluída.");
            case SO_SUPERADMIN_EXCLUI_ADMIN -> throw new OperacaoNaoPermitidaException("Só o superadmin exclui administradores.");
            case SEM_PERMISSAO -> throw new OperacaoNaoPermitidaException("Você não tem permissão para esta ação.");
        }
        log.info("Conta excluída: id={}, por id={}", alvo.id(), ator.id());
    }

    // ---- Regras de exclusão --------------------------------------------------------------

    enum Exclusao {
        PODE,
        NAO_VISIVEL,
        PROPRIA_CONTA,
        PROTEGIDA,
        SO_SUPERADMIN_EXCLUI_ADMIN,
        SEM_PERMISSAO
    }

    /**
     * A única fonte da regra de exclusão: a rota {@code DELETE} e o {@code podeExcluir} da listagem
     * usam esta mesma decisão. A ordem importa: o superadmin real e a própria conta vêm primeiro; para
     * o ADMIN, o que é de outra empresa não é visível (404) antes de ser protegido (403); a conta
     * semeada é protegida para todos; só então valem as regras por papel.
     */
    static Exclusao avaliarExclusao(ContaAutenticada ator, Conta alvo) {
        if (alvo.superadminReal()) {
            // Ninguém exclui o superadmin real; e quem não é ele nem sabe que ele existe.
            return alvo.id() == ator.id() ? Exclusao.PROPRIA_CONTA : Exclusao.NAO_VISIVEL;
        }
        if (alvo.id() == ator.id()) {
            return Exclusao.PROPRIA_CONTA;
        }
        if (ator.papel() == Papel.USUARIO) {
            return Exclusao.SEM_PERMISSAO;
        }
        if (ator.papel() == Papel.ADMIN && !Objects.equals(alvo.empresa(), ator.empresa())) {
            // Outra empresa, ou um SUPERADMIN (sem empresa): fora do que o ADMIN enxerga.
            return Exclusao.NAO_VISIVEL;
        }
        if (alvo.semeada()) {
            return Exclusao.PROTEGIDA;
        }
        if (ator.papel() == Papel.ADMIN) {
            return switch (alvo.papel()) {
                case USUARIO -> alvo.protegida() ? Exclusao.PROTEGIDA : Exclusao.PODE;
                case ADMIN -> Exclusao.SO_SUPERADMIN_EXCLUI_ADMIN;
                case SUPERADMIN -> Exclusao.NAO_VISIVEL;
            };
        }
        // SUPERADMIN: o de demonstração não mexe no que o real criou; o real, sim.
        if (ator.superadminReal()) {
            return Exclusao.PODE;
        }
        return alvo.criadaPelaReal() ? Exclusao.PROTEGIDA : Exclusao.PODE;
    }

    // ---- Apoio ---------------------------------------------------------------------------

    private static final String USUARIO_EM_USO = "Esse nome de usuário já está em uso.";
    private static final String CONTA_NAO_ENCONTRADA = "Conta não encontrada.";

    private ContaListada listada(ContaAutenticada ator, Conta conta) {
        return new ContaListada(
                conta.id(),
                conta.usuario(),
                conta.papel(),
                conta.empresa(),
                conta.protegida(),
                avaliarExclusao(ator, conta) == Exclusao.PODE,
                conta.criadaEm());
    }

    /** O papel de uma conta nova: só ADMIN ou USUARIO — ninguém cria SUPERADMIN pela API. */
    private static Papel papelDeNovaConta(String informado) {
        String mensagem = "Informe o papel da nova conta: ADMIN ou USUARIO.";
        if (informado == null || informado.isBlank()) {
            throw new IllegalArgumentException(mensagem);
        }
        try {
            Papel papel = Papel.valueOf(informado.trim().toUpperCase(Locale.ROOT));
            if (papel == Papel.SUPERADMIN) {
                throw new IllegalArgumentException(mensagem);
            }
            return papel;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(mensagem);
        }
    }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_DA_SENHA) {
            throw new IllegalArgumentException("A senha precisa ter pelo menos " + TAMANHO_MINIMO_DA_SENHA + " caracteres.");
        }
        if (senha.getBytes(StandardCharsets.UTF_8).length > TAMANHO_MAXIMO_DA_SENHA_EM_BYTES) {
            throw new IllegalArgumentException("A senha pode ter no máximo " + TAMANHO_MAXIMO_DA_SENHA_EM_BYTES + " bytes.");
        }
    }
}
