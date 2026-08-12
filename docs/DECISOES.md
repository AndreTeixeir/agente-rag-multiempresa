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
