package br.com.andreteixeira.agenterag.ingestao;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Estratégia padrão (decisão confirmada, não reabrir): divide o Markdown por
 * cabeçalho ({@code #} a {@code ######}) primeiro, depois aplica o chunking
 * de tamanho fixo com overlap ({@link DocumentSplitters#recursive}) dentro de
 * cada seção. É o que permite preencher o metadado {@code secao} de cada
 * chunk com o título da seção de onde ele veio.
 * <p>
 * Conteúdo antes do primeiro cabeçalho (ex.: bloco de metadados no topo do
 * documento) vira chunk(s) com {@code secao = null} — não há cabeçalho real
 * para atribuir, e inventar um seria presumir estrutura que o texto não tem.
 */
public class HybridChunkingStrategy implements ChunkingStrategy {

    private static final Pattern HEADING = Pattern.compile("^#{1,6}\\s+(.+?)\\s*$");

    private final DocumentSplitter splitter;

    public HybridChunkingStrategy(int maxSegmentSizeInChars, int maxOverlapSizeInChars) {
        this.splitter = DocumentSplitters.recursive(maxSegmentSizeInChars, maxOverlapSizeInChars);
    }

    @Override
    public List<Chunk> split(String documentoCompleto) {
        List<Chunk> chunks = new ArrayList<>();
        for (Secao secao : dividirPorSecao(documentoCompleto)) {
            for (String texto : splitTexto(secao.texto())) {
                chunks.add(new Chunk(texto, secao.titulo()));
            }
        }
        return chunks;
    }

    private List<String> splitTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return splitter.split(Document.from(texto)).stream()
                .map(TextSegment::text)
                .toList();
    }

    private List<Secao> dividirPorSecao(String conteudoMarkdown) {
        List<Secao> secoes = new ArrayList<>();
        String[] linhas = conteudoMarkdown.split("\n", -1);

        String tituloAtual = null;
        StringBuilder textoAtual = new StringBuilder();

        for (String linha : linhas) {
            Matcher matcher = HEADING.matcher(linha);
            if (matcher.matches()) {
                if (!textoAtual.isEmpty()) {
                    secoes.add(new Secao(tituloAtual, textoAtual.toString()));
                }
                tituloAtual = matcher.group(1);
                textoAtual = new StringBuilder();
            } else {
                textoAtual.append(linha).append('\n');
            }
        }
        if (!textoAtual.isEmpty()) {
            secoes.add(new Secao(tituloAtual, textoAtual.toString()));
        }
        return secoes;
    }

    private record Secao(String titulo, String texto) {
    }
}
