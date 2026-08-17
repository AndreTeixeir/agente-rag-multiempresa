package br.com.andreteixeira.agenterag.avaliacao;

import java.util.List;

/**
 * Um caso de teste já achatado de {@code avaliacao.yaml} — uma pergunta, a
 * empresa contra a qual ela deve ser buscada, e o que se espera encontrar.
 * <p>
 * {@code documentosEsperados} tem mais de um elemento só nos dois casos de
 * ambiguidade que citam mais de um documento-fonte (Pinecone vs Qdrant); tem
 * zero elementos quando não há documento certo para comparar (colisão em
 * empresa sem o conceito, ou fora de escopo sem nenhuma fonte real).
 * {@code secaoEsperada} é {@code null} sempre que não há uma única seção
 * certa para comparar (mesmos casos multi-documento, ou fora de escopo sem
 * fonte).
 */
record CasoAvaliacao(
        String id,
        String categoria,
        String empresa,
        String pergunta,
        List<String> documentosEsperados,
        String secaoEsperada,
        boolean recusaEsperada) {
}
