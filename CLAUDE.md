# CLAUDE.md

Agente RAG multi-empresa para o Challenge Alura/Oracle ONE. Responde perguntas sobre
documentos de 3 empresas fictícias (BimBam Buy, Mercado Central 24h, Santo Pegasus).

**Prazo: 19/ago/2026.** Plano completo em `PLANO_EXECUCAO.md`.

---

## Regras de trabalho

1. **Nunca inventar.** Não presumir conteúdo de arquivo, versão de dependência ou
   comportamento de biblioteca. Ler e verificar.
2. **Evidência antes de hipótese.** Erro se diagnostica com log, stack trace ou saída
   real — nunca com suposição. Se não há evidência, colher evidência.
3. **Ler e reportar antes de editar.** Antes de tocar em arquivos, informar o estado
   atual do que será alterado.
4. **Uma edição por vez, com aprovação.** Nunca aplicar lote de mudanças sem gate.
5. **Não commitar nem fazer push.** André commita e empurra manualmente.
6. **Entregar arquivo completo corrigido**, nunca trecho parcial.
7. **Um passo de cada vez.** Concluir e confirmar antes de avançar.

---

## Relatório de tarefa

Ao concluir qualquer tarefa, gerar `.md` em `~/Downloads/relatorios-agente-rag/`
com nome `AAAA-MM-DD-<tarefa>.md`. **Fora do repositório.**

Estrutura:
- O que foi feito
- Evidências (comandos executados, saídas, logs)
- O que falhou ou ficou pendente
- Próximo passo sugerido

---

## Stack (fixa — não substituir sem autorização)

- Java 21, Spring Boot 4.1.0, Maven
- LangChain4j `1.18.1` (core) / `1.18.1-beta28` (módulos beta) — usar variantes
  `-spring-boot4-starter`, publicadas na mesma versão da linha principal
  (**não** Spring AI)
- LangGraph4j `1.9.0-beta2` + `langgraph4j-postgres-saver` `1.9.0-beta2`
  (`langgraph4j-core` não depende de Spring)
- PostgreSQL + pgvector (vetores e checkpoints no mesmo banco)
- Embeddings: ONNX local — fallback `text-embedding-004`
- LLM: **`gemini-2.5-flash`** (modelo fixado; não trocar por alias genérico)
- Interface: HTML/JS servido pelo Spring Boot
- Testes: JUnit 5 + Testcontainers

**Versões fixadas no `pom.xml`.** Não usar range nem `LATEST`.

Ao introduzir as dependências acima, rodar `mvn dependency:tree` e checar
conflito de versões contra o `langgraph4j-postgres-saver` antes de seguir.

---

## Arquitetura

Camadas simples — **não** hexagonal.

Duas portas isoladas, e apenas duas:
- `EmbeddingProvider` — permite trocar ONNX por API
- `VectorStore` — permite trocar pgvector por in-memory

**Sem** Spring Security, **sem** JWT. Documentos públicos, sem usuário autenticado.

### Fluxo do agente

Empresa é **identificada no chat** na primeira interação e fica **fixa até o fim da
sessão**. O sistema não adivinha empresa, não compara empresas, não troca de empresa
no meio. Trocar exige nova conversa.

Nós do grafo: identificação → reescrita de consulta (só se há histórico) →
recuperação → avaliação de relevância → resposta ou admissão de ausência.

Estado: empresa + histórico (janela deslizante). Thread ID por sessão (UUID do front).

### Anti-alucinação (3 camadas, obrigatórias)

1. Limiar de score no retrieval — **calibrado empiricamente**, nunca chutado
2. Instrução no prompt: responder só com base no contexto, admitir ausência
3. Citação de fonte (documento + seção) em toda resposta

Log por consulta: score do melhor chunk, se passou do limiar, caminho seguido.

---

## Ingestão

`IngestionService` único, com dois gatilhos:
- Perfil `ingest` → `ApplicationRunner` ingere e encerra (produção)
- `POST /api/admin/ingest` → **apenas** perfil `dev`

Idempotente: `count() > 0` pula; flag `--force` sobrescreve.
**Nunca ingerir no boot da aplicação web.**

Chunking: `DocumentSplitters.recursive` (tamanho fixo com overlap), com tamanho e
overlap em `application.yml`. Estratégia atrás de interface — trocar por "por seção"
deve ser configuração, não refatoração. A escolha final é definida por medição na
Etapa 4, não no papel.

Metadados por chunk: `empresa`, `documento`, `secao`, `pagina`.

---

## Testes

- Unidade e integração rodam em `mvn test`
- Testcontainers para Postgres real
- Conjunto de avaliação em `src/test/resources/avaliacao.yaml`
- **Testes que chamam o LLM levam `@Tag("llm")`** e ficam fora do `mvn test` padrão —
  custam dinheiro e são lentos
- **Nenhum valor esperado entra em asserção sem ter sido lido no documento fonte**

---

## Documentos

- PDFs originais: `src/main/resources/documentos/pdf/` — **não recommitar**
- Markdown convertido: `src/main/resources/documentos/md/`
- Conversão feita fora do código, uma vez

---

## Git

- Começa na `main`
- Depois `feature/<etapa>` por etapa, merge `--no-ff`
- Commits atômicos por etapa concluída
- Repositório **público**

---

## Ambiente

**Local (desenvolvimento):**
- `~/Desktop/Projetos/agente-rag-multiempresa`
- Arquitetura **x86** (VAIO, Ryzen 5), Debian
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`

**Deploy (OCI):**
- Ampere A1 — **ARM64 (aarch64)**, 2 OCPU / 12 GB, Ubuntu 24.04
- `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64`
- ⚠️ Imagem Docker construída em x86 **não roda** aqui. Construir na VM ou usar
  `buildx --platform linux/arm64`.
- ⚠️ Liberar porta na Security List da VCN **e** no firewall do sistema operacional.
  Só a VCN não basta.

**Segredos:** API key do Gemini em variável de ambiente. **Nunca commitar.**
