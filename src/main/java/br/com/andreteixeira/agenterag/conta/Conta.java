package br.com.andreteixeira.agenterag.conta;

import java.time.Instant;

/**
 * Uma linha da tabela {@code conta} ({@code V5__contas.sql}).
 * <p>
 * {@code criadaPelaReal} vem de uma subconsulta, não de uma coluna: é verdadeiro quando a
 * conta que a criou é o superadmin real. {@link #protegida()} junta os três motivos de
 * proteção. O {@code toString} deixa o hash da senha de fora, para ele nunca ir parar num log.
 */
public record Conta(
        long id,
        String usuario,
        String senhaHash,
        Papel papel,
        String empresa,
        boolean semeada,
        boolean superadminReal,
        Long criadaPor,
        boolean criadaPelaReal,
        Instant criadaEm) {

    /** Semeada, ou superadmin real, ou criada por ele: as contas de demonstração não as alteram nem excluem. */
    public boolean protegida() {
        return semeada || superadminReal || criadaPelaReal;
    }

    @Override
    public String toString() {
        return "Conta[id=" + id + ", usuario=" + usuario + ", papel=" + papel + ", empresa=" + empresa + "]";
    }
}
