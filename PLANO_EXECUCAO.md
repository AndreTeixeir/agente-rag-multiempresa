# Plano de Execução — agente-rag-multiempresa

**Challenge RAG — Alura / Oracle ONE (Tech AI Builder)**
Prazo final: **22/ago/2026, 23:59**
Repositório: `agente-rag-multiempresa` (público, `AndreTeixeir`)
Local: `~/Desktop/Projetos/agente-rag-multiempresa`

**Revisão 3 — 22/ago/2026.** Prazo estendido para 22/ago. Este bloco registra as
divergências entre o que foi planejado e o que foi construído. **As etapas abaixo não foram
reescritas**: elas são o registro do que se decidiu em 11/ago, e a diferença entre plano e
execução é informação, não erro a esconder. Só a tabela de riscos foi corrigida, por afirmar
fato presente e não histórico.

| O plano previa | O projeto usa | Por quê |
|---|---|---|
| `text-embedding-004` como fallback | `gemini-embedding-001`, 768 dim, normalização L2 manual | modelo desligado pelo Google em 14/jan/2026, no meio do projeto |
| ONNX local como caminho primário de embedding | descartado | desnecessário depois que o caminho de API se provou viável dentro do free tier |
| `gemini-2.5-flash` | `gemini-3.6-flash` | o 2.5 passou a responder 404 |
| Spring Boot 3.x | Spring Boot 4.1.0 | |
| Chunking recursivo simples como padrão | híbrido por cabeçalho (719 chunks) | medido na Etapa 4.3: o recursivo perde o metadado `secao` e não consegue citar seção |
| 5 nós no grafo | 3 nós | recuperação, limiar e geração ficaram dentro do `RespostaService`, chamado pelo nó `responder` |
| Calendário encerrando em 19/ago | encerrado em 22/ago | prazo estendido oficialmente |

**Revisão 2 — 11/ago/2026.** Calendário recalibrado para começar hoje; chunking passa a ser
simples-primeiro-com-medição; conjunto de avaliação antecipado para a Etapa 4; spikes movidos
para projeto descartável fora do repositório.

---

## Regra de ouro do plano

Cada etapa termina com **algo funcionando e commitado**. Nunca começar a etapa seguinte com a
anterior pela metade. Se o tempo apertar, o que já está commitado é entregável.

**Corte mínimo de segurança:** uma empresa + RAG + deploy + README satisfaz 100% do enunciado.
Tudo além disso é bônus.

---

## Calendário (recalibrado — início 11/ago)

> **Nota da Revisão 3:** o prazo foi estendido para 22/ago e o calendário abaixo é o que se
> planejou em 11/ago, mantido como registro. Na execução real as Etapas 7 e 8 caíram em
> 19–21/ago, e a Etapa 9 em 22/ago.

| Dia | Data | Foco |
|---|---|---|
| Ter | 11/ago | Etapa 0 (spikes) + Etapa 1 (bootstrap) |
| Qua | 12/ago | Etapa 2 (documentos) + subir "hello world" HTTP na OCI — Showing Projects hoje |
| Qui | 13/ago | Etapa 3 (ingestão) + Etapa 4 (avaliação, retrieval, calibração) |
| Sex | 14/ago | Etapa 5 (grafo) + **registrar domínio e apontar DNS** |
| Sáb | 15/ago | Etapa 6 (interface) + Etapa 7 (deploy completo na OCI) |
| Dom | 16/ago | Etapa 8 (HTTPS + voz) |
| Seg | 17/ago | Etapa 9 (testes) + **gravar evidências — sem exceção** |
| Ter | 18/ago | Etapa 10 (README) + folga ⚠️ risco OCI |
| Qua | 19/ago | Entrega até 23:59 |

⚠️ **18/ago é a data em que a Oracle pode terminar instâncias acima da cota.** Gravar evidência
do deploy até 17/ago. Com vídeo e prints guardados, o que a Oracle fizer no dia 18 vira
irrelevante para a entrega.

---

## ETAPA 0 — Validações de risco (spikes)

**Objetivo:** matar as incertezas que podem invalidar decisões, antes de escrever código de verdade.

**Onde:** `~/Desktop/spikes-rag/` — **projeto descartável, fora do repositório do projeto**, com
`pom.xml` próprio e mínimo. Não é a `main` do projeto; nada daqui é commitado.

### 0.1 — Instância OCI
- Conferir no console a **cota atual** de Always Free A1 da conta antes de dimensionar
- Criar `VM.Standard.A1.Flex` com **2 OCPUs / 12 GB**, região São Paulo
- Imagem: **Ubuntu 24.04 (aarch64)** — apt é o que você já usa no Debian local
- Boot volume padrão 50 GB (cota total 200 GB entre todas as VMs)
- Configurar **Budget com alerta em US$ 1**
- Anotar shape exato, IP público, região e AD
- Investigar de onde veio a cobrança da instância anterior (estava com 4 núcleos / 34 GB)

### 0.2 — ONNX: qualidade e arquitetura (dois testes distintos)
- **0.2a — qualidade em português (local, x86):** carregar modelo e gerar embeddings de duas
  frases próximas e duas distantes; conferir se a similaridade separa corretamente.
  Candidatos: `bge-m3`, `multilingual-e5`
- **0.2b — compatibilidade ARM64 (na instância OCI):** instalar Java 21 + Maven, rodar o mesmo
  spike, gerar **um** embedding. Verificar se o Maven resolve o classificador `linux-aarch64`
  do `onnxruntime` ou se ele precisa ser declarado explicitamente.
- **Se 0.2b falhar:** fallback `text-embedding-004`. A porta `EmbeddingProvider` torna a troca barata.

### 0.3 — Grafo mínimo LangGraph4j
- Grafo de 2 nós com aresta condicional, rodando local
- Objetivo: confirmar as assinaturas reais da API da versão que o Maven resolve
- **A versão validada aqui é a que vai fixada no `pom.xml` da Etapa 1**
- **Se falhar:** classificação direta em serviço Java (comportamento externo idêntico)

**Critério de conclusão:** os três validados, com evidência (log/print) no relatório. Versões
exatas anotadas para uso na Etapa 1.

---

## ETAPA 1 — Bootstrap

**Objetivo:** projeto de pé, Git iniciado, aplicação sobe.

**Branch:** `main`

- Criar `~/Desktop/Projetos/agente-rag-multiempresa`
- `git init`, criar repositório **público** no GitHub, primeiro commit
- Spring Boot 3.x + Java 21 + Maven
- `pom.xml` com **versões fixadas** — as validadas na Etapa 0 (LangChain4j, LangGraph4j,
  `langgraph4j-postgres-saver`, pgvector, `onnxruntime`)
- `docker-compose.yml` com Postgres + extensão `vector`
- `.gitignore` (incluir `.env`, `target/`, modelo ONNX se for grande)
- `CLAUDE.md` e `PLANO_EXECUCAO.md` na raiz
- Estrutura de pastas
- `GET /health` respondendo

**Critério:** `mvn spring-boot:run` sobe, `/health` responde, Postgres conecta, repositório
público no ar com o primeiro commit.

---

## ETAPA 2 — Conversão dos documentos

**Objetivo:** 14 PDFs virarem Markdown estruturado.

**Branch:** `feature/documentos`
**Modelo:** Sonnet (tarefa mecânica — não gastar Opus)

- PDFs originais em `src/main/resources/documentos/pdf/`
- Markdown gerado em `src/main/resources/documentos/md/`
- Nomenclatura: `{empresa}-{documento}.md`
- Preservar hierarquia de seções (`##`) e tabelas em sintaxe Markdown
- **Revisão manual obrigatória** das tabelas críticas:
  - Mercado Central: temperaturas, prazos de pagamento, níveis VIP, SPPF
  - Santo Pegasus: matriz SEV, lista de microsserviços com portas
- **Extrair e anotar os fatos que virarão asserções de teste** (ver Etapa 4). Nenhum valor
  esperado entra nos testes sem ter sido lido no documento.

**Critério:** 14 `.md` gerados, tabelas críticas conferidas por você, relatório listando o que
exigiu decisão de formatação e a lista de fatos verificados.

**Atenção:** não recommitar os PDFs depois. Binário alterado incha o histórico do Git
permanentemente.

---

## ETAPA 3 — Ingestão

**Objetivo:** documentos indexados no pgvector com metadados.

**Branch:** `feature/ingestao`

- `IngestionService` — lógica única, agnóstica de quem chama
- **Chunking: começar simples.** `DocumentSplitters.recursive` (tamanho fixo com overlap),
  com tamanho e overlap **em `application.yml`**, não hardcoded. A estratégia fica atrás de
  uma interface pequena para que trocar por "por seção" seja configuração, não refatoração.
- Metadados por chunk: `empresa`, `documento`, `secao`, `pagina`
- Embeddings via porta isolada (`EmbeddingProvider`)
- Persistência via `PgVectorEmbeddingStore` atrás de porta própria (`VectorStore`)
- **Dois gatilhos:**
  - `ApplicationRunner` ativado por perfil `ingest` → ingere e encerra
  - `POST /api/admin/ingest` **apenas** no perfil `dev`
- Guarda de idempotência: `count() > 0` pula, flag `--force` sobrescreve
- **Nunca ingerir no boot da aplicação web**

**Critério:** ingestão roda, banco populado, contagem de chunks por empresa no log. Reingestão
com `--force` funciona.

---

## ETAPA 4 — Conjunto de avaliação, retrieval e calibração

**Objetivo:** buscar corretamente, admitir quando não sabe, e **medir** as duas coisas.

**Branch:** `feature/retrieval`

Esta etapa vem antes da 9 de propósito: não é possível calibrar limiar nem escolher chunking
sem um conjunto de perguntas com resposta conhecida.

### 4.1 — Conjunto de avaliação versionado
Arquivo em `src/test/resources/avaliacao.yaml`, três categorias:
- **Fatos precisos** — pergunta, chunk esperado, fato-chave esperado na resposta
- **Colisões entre empresas** — mesma pergunta, respostas diferentes por empresa
- **Fora de escopo** — deve produzir recusa

⚠️ **Todos os valores esperados são lidos dos documentos na Etapa 2, nunca presumidos.**
Exemplo do que não fazer: assumir "10 dias" para o prazo de arrependimento da BimBam sem abrir
o PDF. Conferir antes de virar asserção.

### 4.2 — Retrieval
- Busca filtrada por `empresa` (metadado)
- **Limiar de score calibrado empiricamente** contra o conjunto de avaliação, incluindo as
  perguntas sem resposta. Não escolher número no papel.
- Prompt do sistema: responder só com base no contexto, admitir ausência
- **Citação de fonte**: documento + seção em toda resposta
- Log por consulta: score do melhor chunk, se passou do limiar, caminho seguido

### 4.3 — Decisão de chunking por medição
- Rodar a avaliação com o chunking `recursive`
- Registrar quantas perguntas recuperam o chunk correto
- **Só se falhar nas tabelas**, implementar o híbrido (por seção com teto) e medir de novo
- Guardar os dois números — vão para o README

**Critério:** pergunta respondível traz resposta com fonte; pergunta sem resposta produz
admissão de ausência, não invenção; números de retrieval registrados.

---

## ETAPA 5 — Grafo LangGraph4j

**Objetivo:** orquestração com estado de sessão.

**Branch:** `feature/grafo`

**Nós:**
1. Identificação de empresa (se ainda não definida no estado)
2. Reescrita de consulta (só quando há histórico)
3. Recuperação
4. Avaliação de relevância (limiar)
5. Resposta ou admissão de ausência

**Estado:** empresa selecionada + histórico (janela deslizante)
**Checkpointing:** `langgraph4j-postgres-saver`, mesmo Postgres do pgvector
**Thread ID:** `UUID.randomUUID()` gerado no front, enviado em cada requisição

**Regra de produto:** empresa fixa até o fim da sessão. Trocar exige nova conversa.

**Critério:** conversa multi-turno funciona; follow-up ("e para perecíveis?") recupera
corretamente; estado persiste entre requisições.

**Bônus:** exportar diagrama do grafo → vai para o README.

---

## ETAPA 6 — API e interface

**Objetivo:** aplicação usável.

**Branch:** `feature/interface`

- `POST /api/chat` recebendo `{ threadId, mensagem }`
- HTML/JS estático servido pelo Spring Boot
- Campo de texto, histórico visível, botão "nova conversa" (limpa thread ID)
- Fontes citadas visíveis na resposta

**Lembrar:** a live foi explícita — **não gastar tempo em estética**. Funcionalidade acima de
aparência.

**Critério:** conversa completa pelo navegador, local.

---

## ETAPA 7 — Deploy OCI

**Objetivo:** aplicação no ar.

**Branch:** `feature/deploy`

⚠️ A instância já existe desde a Etapa 0. O "hello world" HTTP sobe no dia 12, não aqui.

- Build ARM64 — atenção: sua máquina é x86. Ou constrói na própria VM, ou usa `buildx`
  com `--platform linux/arm64`. Imagem construída em x86 **não roda** na Ampere.
- Postgres na instância (container ou nativo)
- Rodar ingestão pelo perfil `ingest`
- **Security List da VCN**: liberar as portas
- **Firewall do sistema operacional**: onde a maioria trava (as imagens OCI vêm com iptables
  restritivo por padrão, mesmo com a VCN liberada)
- Aplicação respondendo no IP público

**Critério:** aplicação acessível de fora, respondendo perguntas.

---

## ETAPA 8 — Domínio, HTTPS e voz

**Objetivo:** entrada por voz funcionando no deploy.

**Branch:** `feature/voz`

### 8.1 — Domínio e certificado (registrar dia 14, certificar dia 16)
- Registrar domínio (~R$ 40-100/ano, serve para portfólio futuro)
- Apontar registro A para o IP da OCI — DNS propaga, por isso o registro é no dia 14
- Caddy ou nginx + Let's Encrypt (Let's Encrypt não emite para IP puro)
- Confirmar HTTPS válido

### 8.2 — Voz
- Web Speech API (`SpeechRecognition`), `pt-BR`
- **Entrada por voz apenas** — resposta em texto, sem síntese
- Botão de microfone **opcional** ao lado do campo de texto
- Testar no Chrome (Firefox não implementa)

**Critério:** falar a pergunta no domínio HTTPS e receber resposta em texto.

**Se o certificado travar:** grava demo de voz em localhost e documenta no README. A entrega
não depende disso.

---

## ETAPA 9 — Testes

**Objetivo:** pirâmide completa.

**Branch:** `feature/testes`

- **Unidade**: chunking, filtro de empresa, limiar, reescrita de consulta
- **Integração**: Testcontainers com Postgres real — ingestão e busca ponta a ponta
- **Avaliação** (expande o conjunto criado na Etapa 4):
  - Assertar **retrieval** (chunk correto recuperado) — determinístico, é o grosso
  - Assertar **fatos-chave** na resposta (contém o número + cita o documento)
  - Assertar **recusa** (pergunta sem resposta → admite ausência)
- **Testes que chamam o LLM levam `@Tag("llm")`** e ficam fora do `mvn test` padrão
  (custam dinheiro e são lentos). Rodam por comando explícito.

**Perguntas de teste — valores a confirmar na Etapa 2 antes de virar asserção:**
- Prazo de arrependimento BimBam (conferir no PDF)
- Prazo de arrependimento Mercado Central (7 dias, Art. 49 CDC — confirmar)
- Vector DB da Santo Pegasus (documentos divergem: Pinecone vs Qdrant — usar como caso de
  ambiguidade legítima)
- Faturamento anual do Mercado Central (não existe → deve recusar)
- Capital da França (fora de escopo → deve recusar)

**Critério:** suíte verde, conjunto de avaliação documentado, `@Tag("llm")` isolado.

---

## ETAPA 10 — README e evidências

**Objetivo:** entregável documental.

**Branch:** `feature/documentacao`

⚠️ **Gravar evidências até 17/ago** — antes do risco de terminação da OCI em 18/ago.

**README (em português) deve conter:**
- Descrição do projeto
- Arquitetura (incluir diagrama do grafo)
- Tecnologias e ferramentas
- Como executar (Docker Compose, perfis, variáveis de ambiente)
- **Exemplos de perguntas** que o agente responde
- **Exemplos de respostas** geradas
- **Números da avaliação de retrieval** (antes/depois do ajuste de chunking)
- Evidência de deploy (link + print ou vídeo)
- Desafios encontrados e como foram superados
- **Decisão documentada:** a conversão PDF→Markdown é etapa de pré-processamento externa;
  os PDFs originais estão versionados como fonte de verdade

**Evidências:**
- Print da aplicação rodando no domínio HTTPS
- Vídeo curto: identificação de empresa → pergunta por voz → resposta com fonte
- Hospedar vídeo (YouTube ou Drive) e linkar no README

**Critério:** README completo, evidências gravadas e linkadas.

---

## Backlog opcional (só se sobrar tempo)

- **Conversor PDF→Markdown em Java dentro do pipeline.** Custo estimado: algumas horas.
  Ganho: elimina qualquer margem sobre a leitura mais literal do enunciado ("código para ler
  e processar o documento"). Com o mecanismo de override — usa `md/` revisado se existir,
  converte o PDF se não — o resultado é idêntico e a leitura fica indiscutível.
- Síntese de voz na resposta (`SpeechSynthesis`)
- Diagrama do grafo exportado via LangGraph4j Studio

---

## Entrega

- Repositório **público**
- Enviar URL do GitHub no curso do Challenge
- **Máximo 5 tentativas** — conferir o link antes
- Baixar badge, autorizar, avaliar o curso
- Baixar certificado em "outras ações"
- Compartilhar no LinkedIn

---

## Riscos mapeados

> **Nota da Revisão 3:** a coluna de desfecho foi acrescentada em 22/ago. Três riscos se
> materializaram — o modelo de embedding e o de geração foram efetivamente descontinuados
> durante o projeto, e a cota do free tier apertou mais do que o previsto.

| Risco | Mitigação planejada | Desfecho |
|---|---|---|
| OCI terminar instância em 18/ago | Instância dentro de 2 OCPU/12 GB + evidência gravada até 17/ago | não ocorreu |
| Capacidade A1 indisponível em São Paulo | Criar a instância **hoje**, não no dia 14 | não ocorreu |
| ONNX não funcionar em ARM64 | Spike 0.2b no dia 1; fallback `text-embedding-004`, porta isolada | **caminho abandonado** — ONNX descartado e o `text-embedding-004` foi desligado pelo Google em 14/jan/2026. A porta `EmbeddingProvider` isolada é o que permitiu a troca por `gemini-embedding-001` sem refatoração |
| LangGraph4j com API divergente | Versão validada no spike e fixada no `pom.xml` | `org.bsc.langgraph4j` 1.9.0-beta2. `CompileConfig.releaseThread()` tem default `true` e apagaria o histórico silenciosamente; corrigido com `releaseThread(false)` |
| Imagem Docker x86 não rodar na Ampere | Build na própria VM ou `buildx --platform linux/arm64` | build feito na própria VM |
| Certificado HTTPS atrasar | Demo de voz em localhost, documentada no README | HTTPS obtido; a voz roda em `https://andreteixeira.dev.br` |
| Tabelas quebradas no chunking | Medição na Etapa 4; híbrido aplicado só onde falhar | híbrido virou padrão para todo o corpus, por medição na Etapa 4.3 |
| Valor de teste presumido em vez de lido | Todos os fatos esperados extraídos e anotados na Etapa 2 | cumprido — `docs/FATOS_VERIFICADOS.md` |
| Cobrança inesperada na OCI | Budget com alerta em US$ 1 | sem cobrança |
| Modelo Gemini descontinuado | Modelo fixado explicitamente | **materializou-se.** O `gemini-2.5-flash` passou a responder 404; o projeto usa `gemini-3.6-flash`, com o nome exato no `application.yml` e nunca um alias genérico |
| Cota do free tier limitar o desenvolvimento | não previsto | **materializou-se.** Teto de requisições de geração por dia moldou a arquitetura de testes: `@Tag("llm")` excluído por padrão, fixture de vetores de consulta versionada e pausa entre chamadas |
