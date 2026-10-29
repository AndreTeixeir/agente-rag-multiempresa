package br.com.andreteixeira.agenterag.resposta;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring do chat model — o Sabiazinho-4 da Maritaca, pela API compatível com a
 * da OpenAI. Isolado do {@code IngestionConfig} (que é sobre as duas portas de
 * ingestão) porque geração de resposta é uma responsabilidade diferente, não
 * uma terceira porta. {@link ChatModel} é usado diretamente pelo
 * {@link RespostaService} (composição manual, sem {@code AiServices} nem
 * {@code RetrievalAugmentor} — decisão da Etapa 4.2b), então não precisa de
 * uma interface própria para trocar de provedor: o próprio {@link ChatModel}
 * da LangChain4j já é a abstração.
 * <p>
 * Só a geração passa pela Maritaca. Os embeddings continuam no Gemini
 * ({@code IngestionConfig}), com chave e propriedades próprias.
 */
@Configuration
@EnableConfigurationProperties(RespostaProperties.class)
public class RespostaConfig {

    @Bean
    public ChatModel chatModel(RespostaProperties properties) {
        RespostaProperties.Chat chat = properties.chat();
        return OpenAiChatModel.builder()
                .baseUrl(chat.baseUrl())
                .apiKey(chat.apiKey())
                .modelName(chat.modelName())
                .timeout(chat.timeout())
                .maxRetries(chat.maxRetries())
                .build();
    }
}
