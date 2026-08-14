package br.com.andreteixeira.agenterag.ingestao;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

/**
 * Ingestão dos 14 documentos Markdown — lógica única, agnóstica de quem chama
 * (perfil {@code ingest} ou {@code POST /api/admin/ingest}).
 * <p>
 * Idempotência é por documento (não pela coleção inteira): antes de ingerir
 * cada arquivo, conta os chunks já gravados com aquele {@code documento} e
 * pula se já houver — sem {@code --force}. Com {@code --force}, apaga só os
 * chunks daquele documento antes de reingerir, não a tabela inteira. Uma
 * falha no meio da ingestão não obriga a recomeçar do zero: rodar de novo
 * sem {@code --force} retoma de onde parou.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private static final String CLASSPATH_PATTERN = "classpath:documentos/md/*.md";

    /**
     * Prefixo do nome de arquivo -> empresa. Mesma convenção de nomenclatura
     * da Etapa 2 ({@code {empresa}-{documento}.md}).
     */
    private static final Map<String, String> PREFIXO_EMPRESA = Map.of(
            "bimbam-", "bimbam",
            "mercado-central-", "mercado-central",
            "santo-pegasus-", "santo-pegasus");

    private final EmbeddingProvider embeddingProvider;
    private final ChunkingStrategy chunkingStrategy;
    private final VectorStore vectorStore;
    private final long pausaEntreChamadasMs;

    public IngestionService(
            EmbeddingProvider embeddingProvider,
            ChunkingStrategy chunkingStrategy,
            VectorStore vectorStore,
            IngestionProperties properties) {
        this.embeddingProvider = embeddingProvider;
        this.chunkingStrategy = chunkingStrategy;
        this.vectorStore = vectorStore;
        this.pausaEntreChamadasMs = properties.pausaEntreChamadasMs();
    }

    /**
     * Ingere os 14 documentos, um de cada vez. Documentos já ingeridos são
     * pulados (idempotência por documento), a menos que {@code force}.
     *
     * @return total de chunks ingeridos nesta execução (documentos pulados não contam)
     */
    public long ingest(boolean force) {
        List<Resource> documentos = listarDocumentos();
        Map<String, Integer> chunksPorEmpresa = new LinkedHashMap<>();
        long total = 0;

        for (Resource documento : documentos) {
            String nomeArquivo = documento.getFilename();
            String empresa = resolverEmpresa(nomeArquivo);

            long jaExistentes = vectorStore.countByDocumento(nomeArquivo);
            if (jaExistentes > 0 && !force) {
                log.info("Pulado {} — já tem {} chunks (use --force para reingerir).", nomeArquivo, jaExistentes);
                continue;
            }
            if (jaExistentes > 0) {
                log.info("--force: apagando {} chunks existentes de {} antes de reingerir.", jaExistentes, nomeArquivo);
                vectorStore.deleteByDocumento(nomeArquivo);
            }

            Instant inicio = Instant.now();
            int gravados = ingerirDocumento(documento, nomeArquivo, empresa);
            Duration duracao = Duration.between(inicio, Instant.now());

            total += gravados;
            chunksPorEmpresa.merge(empresa, gravados, Integer::sum);
            log.info(
                    "Ingerido {} -> {} chunks gravados (empresa={}, {} ms)",
                    nomeArquivo, gravados, empresa, duracao.toMillis());

            if (pausaEntreChamadasMs > 0) {
                dormir(pausaEntreChamadasMs);
            }
        }

        chunksPorEmpresa.forEach((empresa, quantidade) -> log.info("Empresa {}: {} chunks nesta execução", empresa, quantidade));
        log.info("Ingestão concluída: {} chunks gravados nesta execução.", total);
        return total;
    }

    private int ingerirDocumento(Resource documento, String nomeArquivo, String empresa) {
        String conteudo = ler(documento);
        List<ChunkingStrategy.Chunk> chunks = chunkingStrategy.split(conteudo);

        List<String> textos = chunks.stream().map(ChunkingStrategy.Chunk::texto).toList();
        List<float[]> embeddings = embeddingProvider.embedDocuments(textos);

        if (embeddings.size() != chunks.size()) {
            throw new IllegalStateException(
                    "EmbeddingProvider devolveu %d vetores para %d chunks de %s"
                            .formatted(embeddings.size(), chunks.size(), nomeArquivo));
        }

        int gravados = 0;
        for (int i = 0; i < chunks.size(); i++) {
            ChunkingStrategy.Chunk chunk = chunks.get(i);
            ChunkMetadata metadata = new ChunkMetadata(empresa, nomeArquivo, chunk.secao(), null);
            vectorStore.add(embeddings.get(i), chunk.texto(), metadata);
            gravados++;
        }
        log.debug("{}: {} chunks gerados, {} gravados.", nomeArquivo, chunks.size(), gravados);
        return gravados;
    }

    private void dormir(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Pausa entre documentos interrompida.", e);
        }
    }

    private List<Resource> listarDocumentos() {
        try {
            Resource[] resources =
                    new PathMatchingResourcePatternResolver().getResources(CLASSPATH_PATTERN);
            List<Resource> ordenados = new ArrayList<>(List.of(resources));
            ordenados.sort((a, b) -> a.getFilename().compareTo(b.getFilename()));
            return ordenados;
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao listar documentos em " + CLASSPATH_PATTERN, e);
        }
    }

    private String ler(Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + resource.getFilename(), e);
        }
    }

    /**
     * A empresa é sempre determinável — os 14 arquivos seguem a convenção de
     * nomenclatura {@code {empresa}-{documento}.md} definida na Etapa 2. Um
     * arquivo fora dessa convenção é erro de configuração, não um caso de
     * "empresa desconhecida" a tratar em silêncio — por isso falha alto.
     */
    private String resolverEmpresa(String nomeArquivo) {
        return PREFIXO_EMPRESA.entrySet().stream()
                .filter(entry -> nomeArquivo.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Não foi possível determinar a empresa do arquivo " + nomeArquivo
                                + " — nome fora da convenção {empresa}-{documento}.md"));
    }
}
