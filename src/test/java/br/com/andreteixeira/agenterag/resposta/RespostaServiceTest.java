package br.com.andreteixeira.agenterag.resposta;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.andreteixeira.agenterag.ingestao.ChunkMetadata;
import br.com.andreteixeira.agenterag.ingestao.EmbeddingProvider;
import br.com.andreteixeira.agenterag.ingestao.VectorStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Teste unitário do {@link RespostaService}: sem Spring, sem banco e sem rede. Os três
 * colaboradores são dublês escritos aqui mesmo, então o que se verifica é só a lógica do
 * serviço — as duas comportas e o que acontece com a lista de fontes em cada caminho.
 */
class RespostaServiceTest {

    private static final String EMPRESA = "bimbam";
    private static final String PERGUNTA = "Qual o CNPJ da BimBam Buy?";
    private static final int LIMITE = 5;
    private static final double LIMIAR = 0.68;

    private static final String TEXTO_FIXO_DE_AUSENCIA =
            "Não encontrei essa informação nos documentos disponíveis para responder com segurança.";

    private static final VectorStore.SearchResult TRECHO_PAGAMENTO =
            new VectorStore.SearchResult("Aceitamos cartão e pix.", "bimbam-pagamentos", "Métodos de pagamento", 0.83);
    private static final VectorStore.SearchResult TRECHO_PRAZOS =
            new VectorStore.SearchResult("O prazo é de 10 dias.", "bimbam-trocas", "Arrependimento", 0.79);
    private static final VectorStore.SearchResult OUTRO_TRECHO_DE_PAGAMENTO =
            new VectorStore.SearchResult("Parcelamos em até 12 vezes.", "bimbam-pagamentos", "Métodos de pagamento", 0.75);

    private EmbeddingProviderFalso embedding;
    private VectorStoreFalso vectorStore;
    private ChatModelFalso chatModel;
    private RespostaService servico;

    @BeforeEach
    void montaOServico() {
        embedding = new EmbeddingProviderFalso();
        vectorStore = new VectorStoreFalso();
        chatModel = new ChatModelFalso();
        // O serviço só lê limit e threshold; o bloco do chat não participa deste teste.
        servico = new RespostaService(embedding, vectorStore, chatModel, new RespostaProperties(null, LIMITE, LIMIAR));
    }

    @Test
    void buscaVaziaDevolveOTextoFixoDeAusenciaSemFontesENaoChamaOModelo() {
        vectorStore.resultados = List.of();

        RespostaService.Resposta resposta = servico.responder(EMPRESA, PERGUNTA);

        assertThat(resposta.texto()).isEqualTo(TEXTO_FIXO_DE_AUSENCIA);
        assertThat(resposta.fontes()).isEmpty();
        assertThat(chatModel.chamadas).as("comporta 1: o modelo não é chamado").isEmpty();
        // A busca é sempre filtrada pela empresa e usa o limite e o limiar configurados.
        assertThat(embedding.consultas).containsExactly(PERGUNTA);
        assertThat(vectorStore.buscas).containsExactly(EMPRESA + "|" + LIMITE + "|" + LIMIAR);
    }

    @Test
    void quandoOModeloRecusaComAFrasePadraoAsFontesVaoVazias() {
        vectorStore.resultados = List.of(TRECHO_PAGAMENTO, TRECHO_PRAZOS);
        String recusa = "Não encontrei essa informação nos documentos disponíveis.";
        chatModel.texto = recusa;

        RespostaService.Resposta resposta = servico.responder(EMPRESA, PERGUNTA);

        assertThat(resposta.texto()).as("o texto do modelo não muda").isEqualTo(recusa);
        assertThat(resposta.fontes()).isEmpty();
        assertThat(chatModel.chamadas).as("comporta 2: o modelo foi chamado uma vez").hasSize(1);
    }

    @Test
    void respostaNormalComCitacaoDevolveAsFontesNaOrdemDaBuscaSemDuplicatas() {
        // O mesmo documento e seção aparecem em dois trechos: a fonte entra uma vez só, na posição da primeira.
        vectorStore.resultados = List.of(TRECHO_PAGAMENTO, TRECHO_PRAZOS, OUTRO_TRECHO_DE_PAGAMENTO);
        String resposta = "Aceitamos cartão e pix. (Fonte: bimbam-pagamentos — Métodos de pagamento)";
        chatModel.texto = resposta;

        RespostaService.Resposta devolvida = servico.responder(EMPRESA, PERGUNTA);

        assertThat(devolvida.texto()).isEqualTo(resposta);
        assertThat(devolvida.fontes())
                .containsExactly(
                        new RespostaService.Fonte("bimbam-pagamentos", "Métodos de pagamento"),
                        new RespostaService.Fonte("bimbam-trocas", "Arrependimento"));
    }

    @Test
    void textoQueComecaComoRecusaMasCitaFonteMantemAsFontes() {
        vectorStore.resultados = List.of(TRECHO_PAGAMENTO, TRECHO_PRAZOS);
        chatModel.texto = "Não encontrei essa informação sobre o CNPJ, mas os pagamentos estão descritos em "
                + "(Fonte: bimbam-pagamentos — Métodos de pagamento).";

        RespostaService.Resposta resposta = servico.responder(EMPRESA, PERGUNTA);

        assertThat(resposta.fontes())
                .containsExactly(
                        new RespostaService.Fonte("bimbam-pagamentos", "Métodos de pagamento"),
                        new RespostaService.Fonte("bimbam-trocas", "Arrependimento"));
    }

    /**
     * Comportamento documentado, não um defeito a corrigir aqui: a detecção só reconhece a frase
     * padrão da regra 2 do prompt. Uma recusa com outra redação não é identificada e sai com as
     * fontes, como antes — a regra erra para o lado de manter as fontes, nunca de escondê-las.
     */
    @ParameterizedTest
    @ValueSource(strings = {
        "Essa informação não consta nos documentos disponíveis.",
        "Infelizmente não encontrei essa informação nos documentos.",
        "Não localizei esse dado nos documentos disponíveis.",
        "**Não encontrei essa informação** nos documentos disponíveis.",
        "Os documentos não trazem o CNPJ. Não encontrei essa informação."
    })
    void recusaComOutraRedacaoMantemAsFontes(String textoDoModelo) {
        vectorStore.resultados = List.of(TRECHO_PAGAMENTO);
        chatModel.texto = textoDoModelo;

        RespostaService.Resposta resposta = servico.responder(EMPRESA, PERGUNTA);

        assertThat(resposta.texto()).isEqualTo(textoDoModelo);
        assertThat(resposta.fontes()).containsExactly(new RespostaService.Fonte("bimbam-pagamentos", "Métodos de pagamento"));
    }

    /** A recusa é reconhecida sem espaços nas pontas e sem diferenciar maiúsculas de minúsculas. */
    @ParameterizedTest
    @ValueSource(strings = {
        "  \n Não encontrei essa informação nos documentos disponíveis.  \n",
        "NÃO ENCONTREI ESSA INFORMAÇÃO nos documentos disponíveis.",
        "não encontrei essa informação nos documentos disponíveis.",
        "Não encontrei essa informação"
    })
    void recusaReconhecidaSemDiferenciarEspacosNasPontasNemMaiusculas(String textoDoModelo) {
        vectorStore.resultados = List.of(TRECHO_PAGAMENTO);
        chatModel.texto = textoDoModelo;

        RespostaService.Resposta resposta = servico.responder(EMPRESA, PERGUNTA);

        assertThat(resposta.texto()).as("o texto do modelo não é aparado nem alterado").isEqualTo(textoDoModelo);
        assertThat(resposta.fontes()).isEmpty();
    }

    /** A citação também vale em minúsculas: na dúvida, as fontes ficam. */
    @Test
    void citacaoEmMinusculasTambemImpedeQueSejaRecusa() {
        vectorStore.resultados = List.of(TRECHO_PAGAMENTO);
        chatModel.texto = "Não encontrei essa informação (fonte: bimbam-pagamentos — Métodos de pagamento).";

        RespostaService.Resposta resposta = servico.responder(EMPRESA, PERGUNTA);

        assertThat(resposta.fontes()).hasSize(1);
    }

    // ---------------------------------------------------------------- dublês

    private static final class EmbeddingProviderFalso implements EmbeddingProvider {

        final List<String> consultas = new ArrayList<>();

        @Override
        public float[] embedQuery(String texto) {
            consultas.add(texto);
            return new float[] {1f};
        }

        @Override
        public List<float[]> embedDocuments(List<String> textos) {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }

        @Override
        public List<float[]> embedQueries(List<String> textos) {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }

        @Override
        public int dimension() {
            return 1;
        }
    }

    private static final class VectorStoreFalso implements VectorStore {

        List<SearchResult> resultados = List.of();
        /** Uma entrada por busca: {@code empresa|limite|limiar}. */
        final List<String> buscas = new ArrayList<>();

        @Override
        public List<SearchResult> search(String empresa, float[] queryEmbedding, int limit, double threshold) {
            buscas.add(empresa + "|" + limit + "|" + threshold);
            return resultados;
        }

        @Override
        public void add(float[] embedding, String text, ChunkMetadata metadata) {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }

        @Override
        public long count() {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }

        @Override
        public long countByEmpresa(String empresa) {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }

        @Override
        public long countByDocumento(String documento) {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }

        @Override
        public void deleteByDocumento(String documento) {
            throw new UnsupportedOperationException("não usado pelo RespostaService");
        }
    }

    private static final class ChatModelFalso implements ChatModel {

        String texto = "";
        final List<ChatRequest> chamadas = new ArrayList<>();

        @Override
        public ChatResponse doChat(ChatRequest requisicao) {
            chamadas.add(requisicao);
            return ChatResponse.builder().aiMessage(AiMessage.from(texto)).build();
        }
    }
}
