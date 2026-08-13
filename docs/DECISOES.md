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

## 2026-08-13 — Desvio de branch na Etapa 2: commits feitos na `main` em vez de `feature/documentos`

O `PLANO_EXECUCAO.md` determina que cada etapa seja desenvolvida em sua própria branch —
a Etapa 2 (conversão dos documentos) deveria ter sido feita em `feature/documentos`. Os
commits da Etapa 2 (`cd80c68` — adição dos 14 PDFs e tabela de proveniência; `9211e4a` —
conversão dos 14 PDFs para Markdown) foram feitos diretamente na `main`. `git branch -a`
confirma que só existe a branch `main`, local e no remoto — nunca houve
`feature/documentos`. O desvio foi identificado em 13/08/2026, com os dois commits já
publicados no GitHub (repositório público).

**Decisão:** não reescrever o histórico do repositório público por causa disso —
`git rebase`/`filter-branch` sobre commits já publicados publicamente reescreveria a
árvore de commits para qualquer colaborador ou avaliador que já tenha clonado ou
observado o repositório, um custo desproporcional ao problema (a branch é uma
convenção de organização do próprio plano, não um requisito do enunciado do desafio).
Registrar o desvio aqui e retomar a convenção de branch por etapa a partir da Etapa 3,
usando `feature/ingestao` conforme o plano.

**Justificativa:** reescrever histórico público é uma operação destrutiva e visível
para terceiros, desproporcional ao ganho (a Etapa 2 já está commitada e funcional na
`main`, que é a branch de entrega final de qualquer forma — o conteúdo não está errado,
só a organização do histórico). Seguir a convenção a partir de agora resolve o desvio
para as etapas futuras sem gerar o risco de uma reescrita de histórico público.

## 2026-08-13 — Notas de transcrição para os 3 achados de coerência aritmética

A varredura de coerência aritmética de 13/08/2026 encontrou 3 contas que não fecham,
todas já presentes no PDF de origem (não introduzidas pela conversão):

1. `santo-pegasus-protocolo-incidentes-sre.md` — o orçamento de erro do SLO (linha
   149) está escrito como "43 minutos e 12 segundos", mas 0,1% de 730 horas equivale a
   43 minutos e 48 segundos.
2. `mercado-central-fornecedores-compras.md` — as faixas de temperatura ideal
   (linhas 216–220) se sobrepõem em dois pontos: Carnes e Aves (0°C a 4°C) com
   Laticínios (2°C a 8°C) entre 2°C e 4°C, e Laticínios com Hortifrúti (6°C a 10°C)
   entre 6°C e 8°C — um mesmo produto se enquadra em duas categorias com tolerâncias
   máximas diferentes.
3. `mercado-central-fornecedores-compras.md` — a fórmula do NFP (linha 47) não tem
   parênteses envolvendo a soma antes do `× 10`; lida literalmente, o `× 10` multiplica
   só a parcela de Capacidade, não o total, o que não produz a escala de 0 a 100 que as
   faixas de classificação do mesmo documento exigem.

Inseri uma nota logo após cada elemento afetado (parágrafo, tabela ou fórmula), como
texto Markdown normal fora do elemento, registrando a conta correta ao lado da
transcrita. **Nenhum valor foi corrigido** dentro de tabelas, fórmulas ou parágrafos —
os três continuam idênticos ao PDF de origem.

**Justificativa:** os três são defeitos do documento de origem, não da conversão — a
conta errada já estava no PDF antes de qualquer transcrição. Corrigir silenciosamente
apagaria o registro de que a fonte tem um erro e faria o Markdown divergir do PDF sem
motivo de fidelidade. Marcar e não corrigir permite que o agente RAG, ao responder,
cite a inconsistência (e a conta certa, se perguntado) em vez de escolher sozinho qual
dos dois números — o do documento ou o matematicamente correto — apresentar como
resposta.
