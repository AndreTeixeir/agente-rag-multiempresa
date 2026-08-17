package br.com.andreteixeira.agenterag.resposta;

import br.com.andreteixeira.agenterag.ingestao.EmbeddingProvider;
import br.com.andreteixeira.agenterag.ingestao.VectorStore;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Recuperação + geração — composição manual e explícita, de propósito
 * (Etapa 4.2b do plano): NÃO usa {@code AiServices} nem
 * {@code RetrievalAugmentor} da LangChain4j. O filtro por empresa
 * ({@link VectorStore#search}) é o requisito que não pode falhar — precisa
 * estar visível aqui, não escondido dentro de uma abstração de RAG pronta.
 * <p>
 * Duas comportas contra alucinação e gasto de cota, além da terceira camada
 * (citação de fonte) exigida pelo {@code CLAUDE.md}:
 * <ol>
 *     <li><b>Comporta 1</b> — se a busca não devolver nenhum chunk acima do
 *     limiar ({@link RespostaProperties#threshold()}), responde admissão de
 *     ausência sem chamar o LLM. Determinístico e não gasta cota de geração.</li>
 *     <li><b>Comporta 2</b> — mesmo com contexto, o prompt de sistema instrui
 *     o modelo a responder só com base nele e a admitir explicitamente
 *     quando o contexto não contém o fato perguntado (o limiar de score
 *     sozinho não separa "tem resposta" de "não tem" com segurança — ver
 *     comentário em {@code application.yml}: casos sem resposta real podem
 *     ficar acima do limiar por serem topicamente relacionados).</li>
 * </ol>
 */
@Service
public class RespostaService {

    private static final Logger log = LoggerFactory.getLogger(RespostaService.class);

    private static final String PROMPT_SISTEMA =
            """
            Você é um assistente de suporte que responde perguntas de clientes com base
            EXCLUSIVAMENTE no contexto fornecido pelo usuário, extraído dos documentos
            internos da empresa. Nunca use conhecimento geral ou prévio para completar
            uma resposta — se a informação não está no contexto, ela não existe para você.

            Regras obrigatórias, sem exceção:
            1. Responda somente com base nos trechos do CONTEXTO fornecido na mensagem do
               usuário.
            2. Se o contexto não contiver a informação necessária para responder à
               pergunta — mesmo que o contexto fale de assuntos parecidos — diga
               explicitamente que não encontrou essa informação nos documentos
               disponíveis. Não tente adivinhar, aproximar ou completar com conhecimento
               geral.
            3. Toda afirmação que vier do contexto deve citar a fonte, no formato
               "(Fonte: <documento> — <seção>)", logo após a afirmação ou ao final da
               resposta.
            """;

    private static final String SEM_CONTEXTO_SUFICIENTE =
            "Não encontrei essa informação nos documentos disponíveis para responder com segurança.";

    private final EmbeddingProvider embeddingProvider;
    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final int limit;
    private final double threshold;

    public RespostaService(
            EmbeddingProvider embeddingProvider, VectorStore vectorStore, ChatModel chatModel, RespostaProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.limit = properties.limit();
        this.threshold = properties.threshold();
    }

    public Resposta responder(String empresa, String pergunta) {
        float[] vetorConsulta = embeddingProvider.embedQuery(pergunta);
        List<VectorStore.SearchResult> resultados = vectorStore.search(empresa, vetorConsulta, limit, threshold);

        if (resultados.isEmpty()) {
            log.info(
                    "empresa={} pergunta=\"{}\" -> busca vazia acima do limiar {} — admissão de ausência sem chamar o LLM",
                    empresa, pergunta, threshold);
            return new Resposta(SEM_CONTEXTO_SUFICIENTE, List.of());
        }

        String contexto = montarContexto(resultados);
        log.info(
                "empresa={} pergunta=\"{}\" -> {} chunks recuperados (score do topo={}), chamando o LLM",
                empresa, pergunta, resultados.size(), resultados.get(0).score());

        ChatResponse chatResponse = chatModel.chat(
                SystemMessage.from(PROMPT_SISTEMA), UserMessage.from("CONTEXTO:\n" + contexto + "\nPERGUNTA: " + pergunta));

        String texto = chatResponse.aiMessage().text();
        List<Fonte> fontes = extrairFontes(resultados);

        return new Resposta(texto, fontes);
    }

    private String montarContexto(List<VectorStore.SearchResult> resultados) {
        StringBuilder contexto = new StringBuilder();
        int indice = 1;
        for (VectorStore.SearchResult resultado : resultados) {
            String secao = resultado.secao() == null ? "(sem seção identificada)" : resultado.secao();
            contexto.append("[Trecho ")
                    .append(indice++)
                    .append(" — documento: ")
                    .append(resultado.documento())
                    .append(", seção: ")
                    .append(secao)
                    .append("]\n")
                    .append(resultado.texto())
                    .append("\n\n");
        }
        return contexto.toString();
    }

    private List<Fonte> extrairFontes(List<VectorStore.SearchResult> resultados) {
        Set<Fonte> fontes = new LinkedHashSet<>();
        for (VectorStore.SearchResult resultado : resultados) {
            fontes.add(new Fonte(resultado.documento(), resultado.secao()));
        }
        return List.copyOf(fontes);
    }

    public record Resposta(String texto, List<Fonte> fontes) {
    }

    public record Fonte(String documento, String secao) {
    }
}
