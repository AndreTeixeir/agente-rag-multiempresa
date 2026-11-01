package br.com.andreteixeira.agenterag.conta;

/**
 * Papel de uma conta. O nome do enum é o valor gravado na coluna {@code papel}
 * ({@code V5__contas.sql}); não renomear sem uma migração.
 */
public enum Papel {
    USUARIO,
    ADMIN,
    SUPERADMIN
}
