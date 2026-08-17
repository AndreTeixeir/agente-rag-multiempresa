package br.com.andreteixeira.agenterag.avaliacao;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Achata as 4 categorias de {@code src/test/resources/avaliacao.yaml} numa
 * lista única de {@link CasoAvaliacao}, uma pergunta por entrada.
 * <p>
 * Duas expansões acontecem aqui, não no YAML (o YAML é a fonte legível por
 * humano; achatar é problema do harness):
 * <ul>
 *     <li>{@code colisoes_entre_empresas}: cada {@code resposta} da lista
 *     vira um caso próprio, com a mesma {@code pergunta_base};</li>
 *     <li>{@code fora_de_escopo} com {@code empresa: qualquer} (a pergunta da
 *     capital da França) vira 3 casos, um por empresa — a promessa é recusa
 *     em qualquer uma das três.</li>
 * </ul>
 * Usa SnakeYAML (já é dependência transitiva do Spring Boot, não precisou
 * adicionar nada no {@code pom.xml}) carregando o YAML em {@code Map}/
 * {@code List} genéricos — o schema do arquivo é heterogêneo entre
 * categorias (nem todo caso tem {@code documento_esperado}/
 * {@code secao_esperada}), então não compensa mapear para POJOs tipados.
 */
final class AvaliacaoYamlLoader {

    private static final List<String> EMPRESAS = List.of("bimbam", "mercado-central", "santo-pegasus");

    private AvaliacaoYamlLoader() {
    }

    @SuppressWarnings("unchecked")
    static List<CasoAvaliacao> carregar(InputStream entrada) {
        Map<String, Object> raiz = new Yaml().load(entrada);
        List<CasoAvaliacao> casos = new ArrayList<>();

        for (Map<String, Object> fato : (List<Map<String, Object>>) raiz.get("fatos_precisos")) {
            casos.add(new CasoAvaliacao(
                    (String) fato.get("id"),
                    "fatos_precisos",
                    (String) fato.get("empresa"),
                    (String) fato.get("pergunta"),
                    listaOuVazia(fato.get("documento_esperado")),
                    (String) fato.get("secao_esperada"),
                    false));
        }

        for (Map<String, Object> caso : (List<Map<String, Object>>) raiz.get("colisoes_entre_empresas")) {
            String idBase = (String) caso.get("id");
            String perguntaBase = (String) caso.get("pergunta_base");
            for (Map<String, Object> resposta : (List<Map<String, Object>>) caso.get("respostas")) {
                String empresa = (String) resposta.get("empresa");
                boolean recusa = "recusa".equals(resposta.get("comportamento_esperado"));
                casos.add(new CasoAvaliacao(
                        idBase + "--" + empresa,
                        "colisoes_entre_empresas",
                        empresa,
                        perguntaBase,
                        listaOuVazia(resposta.get("documento_esperado")),
                        (String) resposta.get("secao_esperada"),
                        recusa));
            }
        }

        for (Map<String, Object> caso : (List<Map<String, Object>>) raiz.get("fora_de_escopo")) {
            String id = (String) caso.get("id");
            String pergunta = (String) caso.get("pergunta");
            List<String> documentos = listaOuVazia(
                    caso.containsKey("documento_esperado") ? caso.get("documento_esperado") : caso.get("documento_mencionado"));
            String secao = caso.containsKey("secao_esperada")
                    ? (String) caso.get("secao_esperada")
                    : (String) caso.get("secao_mencionada_no_indice");
            String empresaBruta = (String) caso.get("empresa");

            if ("qualquer".equals(empresaBruta)) {
                for (String empresa : EMPRESAS) {
                    casos.add(new CasoAvaliacao(id + "--" + empresa, "fora_de_escopo", empresa, pergunta, documentos, secao, true));
                }
            } else {
                casos.add(new CasoAvaliacao(id, "fora_de_escopo", empresaBruta, pergunta, documentos, secao, true));
            }
        }

        for (Map<String, Object> caso : (List<Map<String, Object>>) raiz.get("ambiguidades_e_defeitos")) {
            List<String> documentos = new ArrayList<>(listaOuVazia(caso.get("documento_esperado")));
            List<Map<String, Object>> trechos = (List<Map<String, Object>>) caso.get("trechos_literais");
            if (trechos != null) {
                for (Map<String, Object> trecho : trechos) {
                    Object documento = trecho.get("documento");
                    if (documento != null && !documentos.contains(documento)) {
                        documentos.add((String) documento);
                    }
                }
            }
            casos.add(new CasoAvaliacao(
                    (String) caso.get("id"),
                    "ambiguidades_e_defeitos",
                    (String) caso.get("empresa"),
                    (String) caso.get("pergunta"),
                    documentos,
                    (String) caso.get("secao_esperada"),
                    false));
        }

        return casos;
    }

    private static List<String> listaOuVazia(Object valor) {
        return valor == null ? List.of() : List.of((String) valor);
    }
}
