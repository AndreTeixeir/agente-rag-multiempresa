package br.com.andreteixeira.agenterag.controller;

import br.com.andreteixeira.agenterag.ingestao.IngestionService;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gatilho de desenvolvimento: {@code POST /api/admin/ingest}, só existe no
 * perfil {@code dev} — não fica exposto em produção.
 */
@RestController
@Profile("dev")
public class AdminIngestController {

    private final IngestionService ingestionService;

    public AdminIngestController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/api/admin/ingest")
    public Map<String, Object> ingest(@RequestParam(name = "force", defaultValue = "false") boolean force) {
        long chunks = ingestionService.ingest(force);
        return Map.of("chunksIngeridos", chunks);
    }
}
