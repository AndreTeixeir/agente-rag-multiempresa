# Registro de decisões técnicas

## 2026-08-11 — Correção do modelo de embeddings: text-embedding-004 está desligado

O `PLANO_EXECUCAO.md` e o `CLAUDE.md` ainda citam `text-embedding-004` como fallback
de embeddings via API. Essa informação está desatualizada: o Google desligou o
`text-embedding-004` em 14/01/2026 e o modelo não existe mais na API. O substituto é
`gemini-embedding-001`.

Esta entrada é só o registro da correção. Nenhuma dependência de embeddings foi
instalada agora — isso fica para a etapa de indexação.

### Consequências a aplicar quando a etapa de indexação chegar

- **Dimensão de saída**: `gemini-embedding-001` devolve 3072 dimensões por padrão.
  O modelo usa Matryoshka Representation Learning e aceita o parâmetro
  `outputDimensionality`. Pedir **768** explicitamente para manter a coluna
  `vector(768)` do pgvector.
- **Normalização L2**: ao reduzir a dimensão abaixo de 3072, o vetor não vem
  normalizado em L2. É preciso normalizar antes de gravar, senão a similaridade de
  cosseno fica distorcida. Verificar se a LangChain4j já faz essa normalização
  internamente antes de assumir que sim.
- **Limite de entrada**: 2048 tokens por chamada. Dimensionar o tamanho do chunk
  (`DocumentSplitters.recursive`) considerando esse limite.
- **Cota**: confirmar a cota do endpoint de embeddings antes da ingestão em massa —
  é uma cota separada da cota do endpoint de geração (`gemini-2.5-flash`).
- **ONNX/e5-small-v2**: a reprovação desse modelo em português continua válida; os
  números da tabela comparativa já levantados seguem servindo de justificativa no
  README. Só o modelo de fallback via API muda, de `text-embedding-004` para
  `gemini-embedding-001`.

## 2026-08-13 — Notas de transcrição em `santo-pegasus-guia-engenharia-frontend.md`

A verificação de fidelidade de 13/08/2026 (dois extratores de PDF independentes,
geometria de glifo e renderização em pixel) confirmou que **faltam 4 asteriscos no
próprio PDF de origem**, em 3 trechos de código de
`santo-pegasus-guia-engenharia-frontend.md` (`staleTime: 60 1000`, `import as Sentry`
e o bloco `tailwind.config.js`). O Markdown transcreve o PDF fielmente — o problema é
do documento original, não da conversão.

Inseri uma nota logo após cada um dos 3 blocos de código afetados, como texto normal
de Markdown (fora do bloco de código, sem comentário HTML), registrando a ausência do
asterisco e que o trecho como impresso não é sintaticamente válido. O conteúdo dentro
dos blocos de código **não foi alterado** — continua idêntico ao PDF.

**Justificativa:** o Markdown precisa permanecer fiel ao PDF (essa fidelidade é a
garantia do RAG), mas sem a nota o agente teria apenas o código quebrado no contexto
recuperado e poderia silenciosamente "consertar" o trecho ao responder, ou apresentá-lo
como correto, sem informar ao usuário que a falha vem do documento original. A nota
fica fora do bloco de código, como texto comum, para ser recuperada junto com o trecho
na busca semântica — um comentário HTML não seria indexado e não cumpriria esse papel.

## 2026-08-13 — Marcação das 4 seções ausentes no índice de `santo-pegasus-arquitetura-microsservicos.md`

O índice ("Tabla de Conteúdos") deste documento lista 15 seções, mas o corpo do PDF de
origem termina na seção 11 — confirmado por extração completa das 12 páginas físicas do
arquivo (ver `2026-08-12-etapa-2-conversao-13.md`). As seções 12 (Mapa de Squads e
Ownership), 13 (Roadmap Técnico), 14 (Architecture Decision Records) e 15 (Disposições
Finais) não existem no documento original.

**Decisão:** não remover as 4 linhas do índice. Marquei cada uma com o sufixo
*(seção não presente no documento de origem)* e inseri uma nota de transcrição logo
após o índice, fora dele, como texto Markdown normal.

**Justificativa:** remover as linhas do índice quebraria a fidelidade ao PDF e apagaria
o registro de que o próprio documento está incompleto. Marcar em vez de remover
preserva a fidelidade e transforma a lacuna em contexto recuperável pela busca
semântica — o agente RAG pode responder que a seção não existe no documento, em vez de
preencher a lacuna sozinho (alucinação) ou simplesmente não encontrar nada e admitir
ausência sem explicação.
