package br.com.andreteixeira.agenterag.avaliacao;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * Fixture versionável dos embeddings de consulta do conjunto de avaliação —
 * {@code src/test/resources/query-embeddings-fixture.json}.
 * <p>
 * Motivo (Etapa 9 do plano): a asserção de retrieval precisa ser
 * determinística e rodar de graça — se cada execução reembedar as perguntas,
 * o teste fica dependente de cota da API e deixa de ser determinístico
 * (o mesmo texto pode, em teoria, gerar vetores ligeiramente diferentes entre
 * chamadas). Com os vetores gravados uma vez, qualquer teste futuro que
 * precise deles lê deste arquivo, offline.
 * <p>
 * Escrito como JSON simples (números em notação decimal fixa, nunca
 * científica, para não depender de nenhuma particularidade de parser). Lido
 * de volta com SnakeYAML — JSON é um subconjunto de YAML, então o mesmo
 * parser que já lê {@code avaliacao.yaml} lê este arquivo sem precisar de
 * nenhuma dependência de JSON adicional (o projeto tem duas versões de
 * Jackson no classpath por causa do Spring Boot 4 + langchain4j, então evitar
 * as duas de propósito aqui evita qualquer ambiguidade de qual usar).
 */
final class FixtureVetores {

    private static final Locale LOCALE = Locale.ROOT;

    private FixtureVetores() {
    }

    static void gravar(
            Path caminho, String modelo, int dimensao, String taskType, Instant geradoEm, Map<String, float[]> vetores)
            throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"_meta\": {\n");
        json.append("    \"modelo\": \"").append(modelo).append("\",\n");
        json.append("    \"dimensao\": ").append(dimensao).append(",\n");
        json.append("    \"taskType\": \"").append(taskType).append("\",\n");
        json.append("    \"geradoEm\": \"").append(geradoEm).append("\",\n");
        json.append("    \"aviso\": \"Fixture valida somente enquanto modelo e dimensao nao mudarem. ")
                .append("Se mudarem, apague este arquivo e rode AvaliacaoRetrievalHarnessTest com ")
                .append("-Dtest=AvaliacaoRetrievalHarnessTest -DexcludedGroups= (opcionalmente com ")
                .append("-DregenerarFixture=true) para regenerar.\"\n");
        json.append("  },\n");
        json.append("  \"vetores\": {\n");

        int i = 0;
        int total = vetores.size();
        for (Map.Entry<String, float[]> entrada : vetores.entrySet()) {
            json.append("    \"").append(entrada.getKey()).append("\": [");
            float[] vetor = entrada.getValue();
            for (int j = 0; j < vetor.length; j++) {
                if (j > 0) {
                    json.append(',');
                }
                json.append(String.format(LOCALE, "%.8f", vetor[j]));
            }
            json.append(']');
            i++;
            if (i < total) {
                json.append(',');
            }
            json.append('\n');
        }
        json.append("  }\n");
        json.append("}\n");

        if (caminho.getParent() != null) {
            Files.createDirectories(caminho.getParent());
        }
        Files.writeString(caminho, json.toString());
    }

    @SuppressWarnings("unchecked")
    static Map<String, float[]> ler(Path caminho, Set<String> idsEsperados) throws IOException {
        try (Reader reader = Files.newBufferedReader(caminho)) {
            Map<String, Object> raiz = new Yaml().load(reader);
            Map<String, Object> brutos = (Map<String, Object>) raiz.get("vetores");
            Map<String, float[]> vetores = new LinkedHashMap<>();
            for (String id : idsEsperados) {
                Object valor = brutos.get(id);
                if (valor == null) {
                    throw new IllegalStateException(
                            "Fixture " + caminho + " não tem vetor para o caso '" + id + "' — apague a fixture e "
                                    + "rode com -Dtest=AvaliacaoRetrievalHarnessTest -DexcludedGroups= para regenerar.");
                }
                List<Number> lista = (List<Number>) valor;
                float[] vetor = new float[lista.size()];
                for (int i = 0; i < lista.size(); i++) {
                    vetor[i] = lista.get(i).floatValue();
                }
                vetores.put(id, vetor);
            }
            return vetores;
        }
    }
}
