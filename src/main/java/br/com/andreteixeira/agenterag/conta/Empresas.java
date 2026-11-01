package br.com.andreteixeira.agenterag.conta;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * As empresas conhecidas pelo sistema, pelo slug — os mesmos três valores que
 * {@code IngestionService.PREFIXO_EMPRESA} grava no metadado {@code empresa} de
 * cada chunk. É a lista única do código de aplicação: o nó {@code identificar_empresa}
 * do grafo e a validação de contas usam esta.
 * <p>
 * {@code IngestionService} ainda mantém a sua própria lista (é {@code private} lá, e
 * acoplar o pacote {@code ingestao} a este não vale uma constante). Se uma quarta
 * empresa for adicionada, <b>os dois lugares</b> precisam ser atualizados — o build
 * não avisa se um for esquecido.
 * <p>
 * {@code LinkedHashSet}, não {@code Set.of(...)}: a ordem de iteração de
 * {@code Set.of(...)} varia entre execuções da JVM, o que deixaria a mensagem de erro
 * de empresa inválida com ordem diferente a cada vez.
 */
public final class Empresas {

    public static final Set<String> SLUGS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of("bimbam", "mercado-central", "santo-pegasus")));

    private Empresas() {
    }

    public static boolean ehValida(String slug) {
        return slug != null && SLUGS.contains(slug);
    }
}
