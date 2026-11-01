package br.com.andreteixeira.agenterag.conta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * As variáveis do superadmin real — {@code app.superadmin.*} em {@code application.yml}, ligadas por
 * placeholder explícito a {@code APP_SUPERADMIN_USUARIO} e {@code APP_SUPERADMIN_SENHA} (o
 * <i>relaxed binding</i> sozinho apontaria para outro nome). Vazias, o superadmin real não existe.
 * <p>
 * O {@code toString} não mostra nada, para nem o usuário nem a senha irem parar num log.
 */
@ConfigurationProperties(prefix = "app.superadmin")
public record SuperadminProperties(@DefaultValue("") String usuario, @DefaultValue("") String senha) {

    public SuperadminProperties {
        usuario = usuario == null ? "" : usuario;
        senha = senha == null ? "" : senha;
    }

    @Override
    public String toString() {
        return "SuperadminProperties[usuario=***, senha=***]";
    }
}
