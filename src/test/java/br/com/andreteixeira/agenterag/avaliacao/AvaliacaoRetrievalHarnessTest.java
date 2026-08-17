package br.com.andreteixeira.agenterag.avaliacao;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.ingestao.EmbeddingProvider;
import br.com.andreteixeira.agenterag.ingestao.VectorStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Harness de medição da Etapa 4.2a do plano — só busca e medição, sem geração
 * de resposta (isso é 4.2b). Lê {@code avaliacao.yaml}, embeda todas as
 * perguntas numa única chamada em lote, busca no pgvector real (os 719 chunks
 * já ingeridos) e reporta a distribuição de scores por categoria. NÃO fixa
 * nenhum limiar — quem decide isso é quem lê o relatório.
 * <p>
 * {@code @Tag("llm")}: pode fazer uma chamada real à API do Gemini (só na
 * primeira execução, ou com {@code -DregenerarFixture=true} — ver
 * {@link FixtureVetores}), então fica fora do {@code mvn test} padrão, mesma
 * convenção de {@code GeminiEmbeddingSmokeTest}. Rodar explicitamente com:
 * <pre>{@code mvn test -Dtest=AvaliacaoRetrievalHarnessTest -DexcludedGroups=}</pre>
 */
@Tag("llm")
@SpringBootTest
class AvaliacaoRetrievalHarnessTest {

    private static final Logger log = LoggerFactory.getLogger(AvaliacaoRetrievalHarnessTest.class);

    private static final Path FIXTURE_PATH = Path.of("src/test/resources/query-embeddings-fixture.json");
    private static final int LIMITE_POR_BUSCA = 5;
    private static final int LIMITE_SEGMENTOS_POR_CHAMADA_HTTP = 100; // GoogleAiEmbeddingModel.MAX_NUMBER_OF_SEGMENTS_PER_BATCH
    private static final String MODELO = "gemini-embedding-001";
    private static final int DIMENSAO = 768;
    private static final String TASK_TYPE = "RETRIEVAL_QUERY";

    private static final Set<String> STOPWORDS = Set.of(
            "qual", "quais", "quanto", "quantos", "quanta", "quantas", "como", "para", "com", "sem", "que", "uma",
            "umas", "uns", "um", "das", "dos", "de", "da", "do", "em", "no", "na", "nos", "nas", "por", "ao", "aos",
            "seu", "sua", "seus", "suas", "segundo", "sobre", "está", "esta", "este", "isso", "essa", "esse", "não",
            "são", "tem", "vai", "ser", "the", "and");

    @Autowired
    private EmbeddingProvider embeddingProvider;

    @Autowired
    private VectorStore vectorStore;

    @Test
    void medeRetrievalContraConjuntoDeAvaliacao() throws IOException {
        List<CasoAvaliacao> casos = carregarCasos();

        log.info("=== CONTAGEM ===");
        log.info("Total de perguntas após achatamento (colisões expandidas por empresa, "
                + "'qualquer' expandido x3): {}", casos.size());
        assertThat(casos.size())
                .as("Precisa ficar abaixo do limite de segmentos por chamada HTTP da lib "
                        + "(GoogleAiEmbeddingModel.MAX_NUMBER_OF_SEGMENTS_PER_BATCH = %d) — "
                        + "se estourar, a execução para aqui, antes de chamar a API.",
                        LIMITE_SEGMENTOS_POR_CHAMADA_HTTP)
                .isLessThan(LIMITE_SEGMENTOS_POR_CHAMADA_HTTP);

        Map<String, float[]> vetores = obterVetores(casos);
        assertThat(vetores).hasSize(casos.size());

        List<ResultadoCaso> resultados = new ArrayList<>();
        for (CasoAvaliacao caso : casos) {
            float[] vetor = vetores.get(caso.id());
            assertThat(vetor).as("vetor do caso " + caso.id()).isNotNull();
            List<VectorStore.SearchResult> topN = vectorStore.search(caso.empresa(), vetor, LIMITE_POR_BUSCA, 0.0);
            resultados.add(avaliar(caso, topN));
        }

        logarTabelaPorPergunta(resultados);
        logarDistribuicaoPorCategoria(resultados);
        logarAssinaturaF11(resultados);

        assertThat(resultados).hasSize(casos.size());
        for (ResultadoCaso resultado : resultados) {
            assertThat(resultado.topN())
                    .as("busca de " + resultado.caso().id() + " não devolveu nenhum resultado")
                    .isNotEmpty();
        }
    }

    private List<CasoAvaliacao> carregarCasos() {
        try (InputStream entrada = getClass().getClassLoader().getResourceAsStream("avaliacao.yaml")) {
            assertThat(entrada).as("avaliacao.yaml não encontrado no classpath de teste").isNotNull();
            return AvaliacaoYamlLoader.carregar(entrada);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler avaliacao.yaml", e);
        }
    }

    private Map<String, float[]> obterVetores(List<CasoAvaliacao> casos) throws IOException {
        boolean forcarRegeneracao = Boolean.getBoolean("regenerarFixture");
        Set<String> ids = casos.stream().map(CasoAvaliacao::id).collect(Collectors.toCollection(LinkedHashSet::new));

        if (!forcarRegeneracao && Files.exists(FIXTURE_PATH)) {
            log.info("=== FIXTURE ===");
            log.info("Fixture encontrada em {} — lendo vetores gravados, ZERO chamadas à API nesta execução.",
                    FIXTURE_PATH);
            return FixtureVetores.ler(FIXTURE_PATH, ids);
        }

        log.info("=== CHAMADA À API ===");
        log.info(
                "Fixture ausente (ou -DregenerarFixture=true) — embedando {} perguntas em UMA chamada em lote "
                        + "(task type {}).",
                casos.size(), TASK_TYPE);
        List<String> perguntas = casos.stream().map(CasoAvaliacao::pergunta).toList();

        Instant inicio = Instant.now();
        List<float[]> vetoresGerados = embeddingProvider.embedQueries(perguntas);
        Duration duracao = Duration.between(inicio, Instant.now());

        log.info("Chamadas HTTP à API de embedding nesta execução: 1 (lote de {} textos, {} ms).",
                perguntas.size(), duracao.toMillis());
        assertThat(vetoresGerados).hasSameSizeAs(casos);
        for (float[] vetor : vetoresGerados) {
            assertThat(vetor.length).isEqualTo(DIMENSAO);
        }

        Map<String, float[]> vetores = new LinkedHashMap<>();
        for (int i = 0; i < casos.size(); i++) {
            vetores.put(casos.get(i).id(), vetoresGerados.get(i));
        }

        FixtureVetores.gravar(FIXTURE_PATH, MODELO, DIMENSAO, TASK_TYPE, Instant.now(), vetores);
        log.info("Fixture gravada em {} — próximas execuções não vão chamar a API.", FIXTURE_PATH);

        return vetores;
    }

    private ResultadoCaso avaliar(CasoAvaliacao caso, List<VectorStore.SearchResult> topN) {
        double scoreTopo = topN.isEmpty() ? Double.NaN : topN.get(0).score();

        Integer posicaoChunkCorreto = null;
        for (int i = 0; i < topN.size(); i++) {
            if (caso.documentosEsperados().contains(topN.get(i).documento())) {
                posicaoChunkCorreto = i + 1;
                break;
            }
        }

        boolean documentoBateu = !topN.isEmpty() && caso.documentosEsperados().contains(topN.get(0).documento());
        boolean secaoBateu = documentoBateu
                && caso.secaoEsperada() != null
                && caso.secaoEsperada().equals(topN.get(0).secao());

        return new ResultadoCaso(caso, topN, scoreTopo, documentoBateu, secaoBateu, posicaoChunkCorreto);
    }

    private void logarTabelaPorPergunta(List<ResultadoCaso> resultados) {
        log.info("=== TABELA POR PERGUNTA ===");
        log.info("| id | categoria | empresa | doc_esperado | doc_recuperado | secao_esperada | secao_recuperada "
                + "| score_topo | posicao_correto |");
        log.info("|---|---|---|---|---|---|---|---|---|");
        List<ResultadoCaso> ordenados = resultados.stream()
                .sorted((a, b) -> {
                    int cmp = a.caso().categoria().compareTo(b.caso().categoria());
                    return cmp != 0 ? cmp : a.caso().id().compareTo(b.caso().id());
                })
                .toList();
        for (ResultadoCaso r : ordenados) {
            VectorStore.SearchResult topo = r.topN().get(0);
            log.info(
                    "| {} | {} | {} | {} | {} | {} | {} | {} | {} |",
                    r.caso().id(),
                    r.caso().categoria(),
                    r.caso().empresa(),
                    r.caso().documentosEsperados().isEmpty() ? "(nenhum — recusa esperada)"
                            : String.join(" OU ", r.caso().documentosEsperados()),
                    topo.documento(),
                    r.caso().secaoEsperada() == null ? "(n/a)" : r.caso().secaoEsperada(),
                    topo.secao() == null ? "(null)" : topo.secao(),
                    String.format(Locale.ROOT, "%.4f", r.scoreTopo()),
                    r.posicaoChunkCorreto() == null ? "não veio" : r.posicaoChunkCorreto().toString());
        }
    }

    private void logarDistribuicaoPorCategoria(List<ResultadoCaso> resultados) {
        log.info("=== DISTRIBUIÇÃO DE SCORES POR CATEGORIA (score do topo de cada busca) ===");
        Map<String, List<Double>> scoresPorCategoria = new LinkedHashMap<>();
        for (ResultadoCaso r : resultados) {
            scoresPorCategoria
                    .computeIfAbsent(r.caso().categoria(), k -> new ArrayList<>())
                    .add(r.scoreTopo());
        }
        for (Map.Entry<String, List<Double>> entrada : scoresPorCategoria.entrySet()) {
            List<Double> scores = new ArrayList<>(entrada.getValue());
            java.util.Collections.sort(scores);
            double min = scores.get(0);
            double max = scores.get(scores.size() - 1);
            double mediana = mediana(scores);
            log.info(
                    "categoria={} | n={} | min={} | mediana={} | max={}",
                    entrada.getKey(),
                    scores.size(),
                    String.format(Locale.ROOT, "%.4f", min),
                    String.format(Locale.ROOT, "%.4f", mediana),
                    String.format(Locale.ROOT, "%.4f", max));
        }
    }

    private double mediana(List<Double> valoresOrdenados) {
        int n = valoresOrdenados.size();
        if (n % 2 == 1) {
            return valoresOrdenados.get(n / 2);
        }
        return (valoresOrdenados.get(n / 2 - 1) + valoresOrdenados.get(n / 2)) / 2.0;
    }

    /**
     * Achado #1 do code review externo (F-11): {@code HybridChunkingStrategy}
     * grava o título da seção como metadado, mas nunca inclui a linha do
     * cabeçalho no texto que vai para o embedding. A assinatura desse
     * problema é: o documento certo foi encontrado, mas a seção não — e é
     * ainda mais revelador quando o termo-chave da pergunta aparece
     * literalmente no título da seção esperada (a busca deveria ter achado
     * essa seção com folga, mas o texto embedado dela nunca continha esse
     * termo). Só mede e reporta — nenhuma correção de chunking acontece
     * aqui.
     */
    private void logarAssinaturaF11(List<ResultadoCaso> resultados) {
        log.info("=== ASSINATURA DO ACHADO F-11 (documento bateu, seção não) ===");
        List<ResultadoCaso> candidatos = resultados.stream()
                .filter(r -> r.documentoBateu() && !r.secaoBateu() && r.caso().secaoEsperada() != null)
                .toList();

        log.info("Casos com documento correto e seção esperada definida: {}",
                resultados.stream().filter(r -> r.documentoBateu() && r.caso().secaoEsperada() != null).count());
        log.info("Desses, documento bateu mas seção NÃO bateu: {}", candidatos.size());

        for (ResultadoCaso r : candidatos) {
            Set<String> palavrasPergunta = palavrasChave(r.caso().pergunta());
            Set<String> palavrasSecao = palavrasChave(r.caso().secaoEsperada());
            Set<String> intersecao = new LinkedHashSet<>(palavrasPergunta);
            intersecao.retainAll(palavrasSecao);

            log.info(
                    "id={} | secao_esperada=\"{}\" | secao_recuperada=\"{}\" | score_topo={} | "
                            + "termo_chave_no_titulo={} | termos_compartilhados={}",
                    r.caso().id(),
                    r.caso().secaoEsperada(),
                    r.topN().get(0).secao(),
                    String.format(Locale.ROOT, "%.4f", r.scoreTopo()),
                    !intersecao.isEmpty(),
                    intersecao);
        }
    }

    private static Set<String> palavrasChave(String texto) {
        String semAcento = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String[] tokens = semAcento.split("[^a-z0-9]+");
        Set<String> palavras = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.length() >= 4 && !STOPWORDS.contains(token)) {
                palavras.add(token);
            }
        }
        return palavras;
    }

    private record ResultadoCaso(
            CasoAvaliacao caso,
            List<VectorStore.SearchResult> topN,
            double scoreTopo,
            boolean documentoBateu,
            boolean secaoBateu,
            Integer posicaoChunkCorreto) {
    }
}
