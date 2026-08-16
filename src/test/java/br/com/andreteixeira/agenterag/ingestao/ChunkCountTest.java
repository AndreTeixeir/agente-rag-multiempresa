package br.com.andreteixeira.agenterag.ingestao;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

/**
 * Não chama nenhuma API — só mede quantos chunks cada {@link ChunkingStrategy}
 * produz para os 14 documentos reais, com o mesmo tamanho/overlap de
 * {@code application.yml}. Cobre as duas estratégias (híbrida = padrão, e
 * recursiva pura = linha de base) para a comparação da Etapa 4.3 do plano, e
 * serve de evidência para a estimativa de custo/cota da ingestão.
 */
class ChunkCountTest {

    private static final Logger log = LoggerFactory.getLogger(ChunkCountTest.class);

    private static final int MAX_SEGMENT_SIZE_CHARS = 1000;
    private static final int MAX_OVERLAP_SIZE_CHARS = 200;

    @Test
    void contaChunksReaisDos14Documentos_hibrida() throws IOException {
        contarEReportar("HÍBRIDA (padrão)", new HybridChunkingStrategy(MAX_SEGMENT_SIZE_CHARS, MAX_OVERLAP_SIZE_CHARS));
    }

    @Test
    void contaChunksReaisDos14Documentos_recursivaPura() throws IOException {
        contarEReportar(
                "RECURSIVA PURA (linha de base)", new RecursiveChunkingStrategy(MAX_SEGMENT_SIZE_CHARS, MAX_OVERLAP_SIZE_CHARS));
    }

    private void contarEReportar(String rotulo, ChunkingStrategy chunkingStrategy) throws IOException {
        Resource[] documentos =
                new PathMatchingResourcePatternResolver().getResources("classpath:documentos/md/*.md");
        assertThat(documentos).hasSize(14);

        Map<String, Integer> chunksPorDocumento = new LinkedHashMap<>();
        int total = 0;

        for (Resource documento : documentos) {
            String conteudo = ler(documento);
            int chunksDoDocumento = chunkingStrategy.split(conteudo).size();
            chunksPorDocumento.put(documento.getFilename(), chunksDoDocumento);
            total += chunksDoDocumento;
        }

        log.info("=== {} ===", rotulo);
        chunksPorDocumento.forEach((nome, quantidade) -> log.info("{} -> {} chunks", nome, quantidade));
        log.info("TOTAL [{}]: {} chunks nos 14 documentos (chunk={} chars, overlap={} chars)",
                rotulo, total, MAX_SEGMENT_SIZE_CHARS, MAX_OVERLAP_SIZE_CHARS);

        assertThat(total).isPositive();
    }

    private String ler(Resource resource) {
        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + resource.getFilename(), e);
        }
    }
}
