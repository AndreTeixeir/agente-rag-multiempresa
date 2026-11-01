package br.com.andreteixeira.agenterag.conta;

/**
 * A conta da sessão atual — o principal do {@code Authentication}. Carregada do banco a cada
 * requisição pelo {@code SessaoCookieFilter} (o JWT só diz de quem é o id), então papel e empresa
 * são sempre os de agora: excluir a conta ou o reset diário encerram a sessão na hora.
 * <p>
 * Não leva o hash da senha.
 */
public record ContaAutenticada(long id, String usuario, Papel papel, String empresa, boolean superadminReal) {

    /** O que a interface mostra no lugar do usuário do superadmin real, que nunca aparece. */
    static final String NOME_DO_SUPERADMIN_REAL = "superadmin";

    public static ContaAutenticada de(Conta conta) {
        return new ContaAutenticada(
                conta.id(), conta.usuario(), conta.papel(), conta.empresa(), conta.superadminReal());
    }

    /** O nome para a interface: o usuário, exceto o do superadmin real. */
    public String nomeExibido() {
        return superadminReal ? NOME_DO_SUPERADMIN_REAL : usuario;
    }
}
