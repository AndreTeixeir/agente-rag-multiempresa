# Fatos verificados nos documentos

Todo fato abaixo foi lido diretamente no `.md` gerado (e, para as tabelas críticas, conferido
também contra o PDF de origem — ver `2026-08-13-etapa-2-tabelas-e-fatos.md`). Nenhum valor foi
presumido ou completado por conhecimento geral. Esta lista alimenta as asserções de
`src/test/resources/avaliacao.yaml` na Etapa 4.

Formato de cada fato: **Empresa** · documento · seção — fato em uma linha, seguido do trecho
literal do `.md` que o sustenta (citação exata, sem paráfrase).

---

## 1. Tabelas críticas (Etapa 2 — revisão manual)

### 1.1 Mercado Central — Temperaturas ideais por categoria de produto

**Mercado Central 24h** · `mercado-central-fornecedores-compras.md` · Seção 5.4 (linhas 214–220)

| Categoria | Faixa ideal | Tolerância máxima |
|---|---|---|
| Congelados (carnes, pescados, sorvetes) | Igual ou inferior a -18°C | Até -12°C (recusa abaixo disso) |
| Refrigerados — Carnes e Aves in natura | 0°C a 4°C | Até 7°C |
| Refrigerados — Laticínios e Derivados | 2°C a 8°C | Até 10°C |
| Refrigerados — Hortifrúti | 6°C a 10°C | Até 12°C |
| Temperatura Ambiente Controlada | 15°C a 25°C | Até 28°C em dias de pico de calor |

> `| Congelados (carnes, pescados, sorvetes) | Igual ou inferior a -18°C | Até -12°C (recusa abaixo disso) |`

Conferida célula a célula contra o PDF (página 8) em 13/08/2026 — sem divergência.

### 1.2 Mercado Central — Prazos de pagamento a fornecedores por classe

**Mercado Central 24h** · `mercado-central-fornecedores-compras.md` · Seção 8.1 (linhas 280–287)

| Categoria/Segmento | Classe A | Classe B | Classe C |
|---|---|---|---|
| Secos e Mercearia | 28 dias | 35 dias | 42 dias |
| Perecíveis (Horti/Carnes) | 07 dias | 10 dias | 14 dias |
| Bazar e Higiene | 30 dias | 45 dias | 60 dias |
| Serviços e Insumos Administrativos | 30 dias | 30 dias | 30 dias |

> `| Perecíveis (Horti/Carnes) | 07 dias | 10 dias | 14 dias |`

Conferida célula a célula contra o PDF (páginas 10–11) em 13/08/2026 — sem divergência.

### 1.3 Mercado Central — Níveis do Programa VIP Central

**Mercado Central 24h** · `mercado-central-atendimento-trocas-devolucoes.md` · Seção 8.3 (linhas 304–309)

| Nível | Pontos (12 meses) | Cashback | Benefício adicional |
|---|---|---|---|
| Bronze | 0 a 999 pontos | 0,5% | Preços VIP em etiquetas sinalizadas |
| Prata | 1.000 a 2.999 pontos | 1,0% | + Fila prioritária em horário de pico |
| Gold | 3.000 a 6.999 pontos | 1,5% | + Pré-venda de produtos sazonais |
| Diamante | 7.000 pontos ou mais | 2,0% | + SAC dedicado + Frete grátis ilimitado no App |

> `| Diamante | 7.000 pontos ou mais | 2,0% | Todos os benefícios anteriores + Atendimento SAC dedicado (linha direta) + Frete grátis ilimitado no App |`

Conferida célula a célula contra o PDF (página 10) em 13/08/2026 — sem divergência.

### 1.4 Santo Pegasus — Matriz de Severidade (SEV)

**Santo Pegasus Soluciones** · `santo-pegasus-protocolo-incidentes-sre.md` · Seção 4 (linhas 51–72)

Não é uma tabela em grade — é prosa estruturada em 4 subseções (SEV-1 a SEV-4), formato que
reflete o próprio PDF (também prosa, sem grade nessa seção).

| Nível | Impacto financeiro/hora | Observação |
|---|---|---|
| SEV-1 | Acima de R$ 100.000 | Vazamento de dados sensíveis; notificação ANPD — ver ambiguidade §6.2 |
| SEV-2 | Entre R$ 20.000 e R$ 100.000 | Indisponibilidade parcial regional |
| SEV-3 | Inferior a R$ 20.000 | Sem risco clínico ou de vazamento |
| SEV-4 | Não quantificado | Tratado no backlog do próximo Sprint |

> `- Impacto Financeiro: Projetado acima de R$ 100.000 por hora de inatividade devido a cirurgias canceladas, glosas médicas e impossibilidade de faturamento.` (SEV-1)
> `- Impacto Financeiro: Perdas estimadas variam entre R$ 20.000 e R$ 100.000 por hora.` (SEV-2)
> `- Impacto: Prejuízo financeiro inferior a R$ 20.000 por hora.` (SEV-3)

Conferida contra o PDF (páginas 5–6) em 13/08/2026 — sem divergência de atribuição de valor a
nível de severidade.

### 1.5 Santo Pegasus — Microsserviços com portas

**Santo Pegasus Soluciones** · `santo-pegasus-arquitetura-microsservicos.md` · Seção 3.1 (linhas 146–155)

| Serviço | Porta | Banco de Dados |
|---|---|---|
| auth-service | 8081 | PostgreSQL |
| user-service | 8082 | PostgreSQL |
| agendio-scheduling-service | 8083 | PostgreSQL |
| agendio-notification-service | 8084 | — (stateless) |
| payment-service | 8085 | PostgreSQL |
| medical-records-service | 8086 | MongoDB |
| ai-assistant-service | 8087 | Pinecone (Vector) |
| audit-service | 8088 | PostgreSQL |

> `| ai-assistant-service | 8087 | Assistência IA (RAG) | Pinecone (Vector) | Squad IA |`

Conferida célula a célula contra o PDF (páginas 6–7) em 13/08/2026 — sem divergência.

### 1.6 Mercado Central — SPPF

Já conferida em revisão anterior à desta tarefa — não repetida aqui por instrução explícita.

---

## 2. BimBam Buy — prazos, percentuais e valores monetários

**BimBam Buy** · `bimbam-reembolsos-devolucoes.md` · Seção 5.1 — prazo de arrependimento: 10 dias
corridos após o recebimento.
> `O cliente pode solicitar devolução por arrependimento dentro dos 10 dias corridos subsequentes ao recebimento do pedido, desde que o produto cumpra com os requisitos de elegibilidade.`

**BimBam Buy** · `bimbam-reembolsos-devolucoes.md` · (linha 130) — prazo de processamento do
reembolso após aprovação: 5 a 10 dias úteis.
> `Uma vez aprovado, o reembolso é processado em um prazo de 5 a 10 dias úteis, dependendo do método de pagamento e do país de origem da compra.`

**BimBam Buy** · `bimbam-reembolsos-devolucoes.md` · (linha 367) — prazo de confirmação de
recebimento de uma solicitação: 24 horas úteis.
> `- Aviso de recebimento: imediato ou em até 24 horas úteis`

**BimBam Buy** · `bimbam-garantia-produtos.md` · (linha 221) — prazo de confirmação de
recebimento de reclamação de garantia: 24 horas úteis.
> `- Confirmação de recebimento: 24 horas úteis`

**BimBam Buy** · `bimbam-garantia-produtos.md` · (linha 76) — prazo de garantia: **não é um valor
fixo único**; varia por produto/categoria/país, informado na ficha do produto.
> `O prazo de garantia pode variar de acordo com o tipo de produto, categoria e país. O prazo aplicável será o informado na ficha do produto ou na confirmação de compra.`

---

## 3. Mercado Central 24h — prazos, percentuais e valores monetários

**Mercado Central 24h** · `mercado-central-atendimento-trocas-devolucoes.md` · Seção 4.1 (linha
200) — prazo de arrependimento: 7 dias corridos, Art. 49 do CDC.
> `Conforme o Art. 49 do CDC, o consumidor tem o prazo de 7 (sete) dias corridos, a contar do recebimento do produto, para desistir da compra.`

**Mercado Central 24h** · mesmo arquivo · (linha 242) — estorno em cartão de crédito: solicitado
à administradora em até 5 dias úteis, aparece em até 2 faturas subsequentes.
> `1. Cartão de Crédito: A solicitação de estorno é enviada à administradora em até 5 dias úteis. O crédito poderá aparecer em até duas faturas subsequentes, dependendo do fechamento do cartão.`

**Mercado Central 24h** · mesmo arquivo · Seção 9 (linhas 334–337) — direitos LGPD com prazos
próprios: Acesso (15 dias corridos), Correção (5 dias úteis), Portabilidade (15 dias corridos).
> `1. Direito de Acesso: [...] Prazo de resposta: até 15 dias corridos.`
> `2. Direito de Correção: [...] Prazo de atualização: até 5 dias úteis.`
> `4. Direito de Portabilidade: [...] Prazo de atendimento: até 15 dias corridos.`

**Mercado Central 24h** · mesmo arquivo · (linha 139) — SLA de resposta geral: 48 horas úteis,
podendo se estender a 5 dias úteis em casos que exijam análise jurídica/fiscal.
> `SLA de Resposta: Retorno humano em até 48 horas úteis, podendo se estender a 5 dias úteis em casos que demandem análise jurídica ou fiscal.`

**Mercado Central 24h** · `mercado-central-fornecedores-compras.md` · Seção 9.1 (linha ~465) —
taxa de antecipação de recebíveis: 2,5% ao mês, pro-rata die.
> `• Taxa aplicada: 2,5% ao mês, calculada em regime pro-rata die (proporcional ao número de dias antecipados).`

**Mercado Central 24h** · mesmo arquivo · tabela de aprovação de OC por faixa de valor (linhas
160–166): até R$ 10.000 → Comprador (4h úteis); R$ 10.000,01–50.000 → Coordenador (8h úteis);
R$ 50.000,01–150.000 → Gerente (24h úteis); R$ 150.000,01–500.000 → Diretor (48h úteis); acima
de R$ 500.000 → Diretoria Executiva (72h úteis).
> `| Até R$ 10.000,00 | Comprador da Categoria | 4 horas úteis |`

**Mercado Central 24h** · `mercado-central-regulamento-procedimentos-sop.md` · (linha 7) — CNPJ
da própria empresa consta no documento: 00.123.456/0001-99.
> `- CNPJ: 00.123.456/0001-99`

---

## 4. Santo Pegasus Soluciones — prazos, percentuais e valores monetários

**Santo Pegasus** · `santo-pegasus-protocolo-incidentes-sre.md` · Seção 4/SEV-1 (linha 55) —
impacto financeiro SEV-1: acima de R$ 100.000/hora; multa ANPD até 2% do faturamento líquido,
limitada a R$ 50 milhões por infração.
> `Isso pode resultar em multas aplicadas pela ANPD de até 2% do faturamento líquido da empresa, limitadas a absurdos R$ 50 milhões por infração (Art. 52, LGPD)`

**Santo Pegasus** · mesmo arquivo · (linha 147) — SLA contratual de disponibilidade: 99,5%.
> `Service Level Agreement (SLA): É o contrato jurídico externo com as redes hospitalares. Fixado em 99,5%.`

**Santo Pegasus** · mesmo arquivo · (linha 98) — descanso mínimo após acionamento noturno: 11
horas consecutivas (Art. 66 CLT).
> `A política dita que ele tenha um período mínimo de 11 horas de repouso consecutivo (Art. 66 da CLT) antes de retomar atividades rotineiras.`

**Santo Pegasus** · mesmo arquivo · (linha 106) — prazo de triagem inicial de incidente: menos de
5 minutos.
> `O engenheiro on-call avalia o impacto (Blast Radius) em menos de 5 minutos.`

**Santo Pegasus** · `santo-pegasus-manual-onboarding.md` · Seção de férias (linha 494) — 30 dias
corridos de férias após 12 meses (período aquisitivo), conforme CLT.
> `Conforme a legislação brasileira, todo colaborador CLT tem direito a 30 dias corridos de férias após completar 12 meses de trabalho (período aquisitivo).`

---

## 5. Colisões entre empresas (mesmo assunto, respostas diferentes)

### 5.1 Prazo de arrependimento — BimBam Buy vs. Mercado Central 24h

- **BimBam Buy**: 10 dias corridos (`bimbam-reembolsos-devolucoes.md`, linha 118, ver §2).
- **Mercado Central 24h**: 7 dias corridos, com base legal explícita no Art. 49 do CDC
  (`mercado-central-atendimento-trocas-devolucoes.md`, linha 200, ver §3).
- **Santo Pegasus**: não se aplica — a empresa não vende produtos ao consumidor final; o conceito
  de "arrependimento de compra" não existe em nenhum dos 5 documentos da Santo Pegasus (confirmado
  por busca — 0 ocorrências).

Caso de colisão de 2 empresas + ausência de conceito na 3ª. Útil para testar que o agente não
confunde 7 dias com 10 dias entre empresas, e que recusa a pergunta corretamente se aplicada à
Santo Pegasus.

### 5.2 Prazo de reembolso/estorno após aprovação — BimBam Buy vs. Mercado Central 24h

- **BimBam Buy**: reembolso processado em 5 a 10 dias úteis após aprovação, prazo único
  (`bimbam-reembolsos-devolucoes.md`, linha 130).
- **Mercado Central 24h**: estorno em cartão de crédito enviado à administradora em até 5 dias
  úteis, mas o crédito só aparece "em até duas faturas subsequentes" — um prazo composto, não um
  número único (`mercado-central-atendimento-trocas-devolucoes.md`, linha 242).
- **Santo Pegasus**: não se aplica — não há reembolso ao consumidor final.

Mesma observação: 2 empresas comparáveis, 3ª fora do domínio.

### 5.3 "SLA" / prazo de resposta — as três empresas, sentidos diferentes

Este é o caso que abrange as três, mas o ponto do teste é justamente que a mesma sigla ("SLA")
e a mesma pergunta genérica ("qual o prazo de resposta?") significam coisas diferentes em cada
empresa — um bom teste de que o agente não mistura contexto entre empresas:

- **BimBam Buy**: não usa o termo "SLA"; compromete-se com confirmação de recebimento de
  solicitação em 24 horas úteis (`bimbam-reembolsos-devolucoes.md`, linha 367, ver §2).
- **Mercado Central 24h**: "SLA de Resposta" = retorno humano ao cliente em até 48 horas úteis,
  podendo se estender a 5 dias úteis (`mercado-central-atendimento-trocas-devolucoes.md`, linha
  139, ver §3).
- **Santo Pegasus**: "SLA" = disponibilidade contratual de 99,5% do sistema (não é prazo de
  resposta a uma pessoa) e, separadamente, o prazo de notificação de incidente à ANPD — que tem
  **dois valores diferentes no mesmo documento** (ver ambiguidade §6.2 abaixo).

---

## 6. Ambiguidades registradas (não resolvidas — decisão de leitura não tomada)

### 6.1 Vector DB da Santo Pegasus: Pinecone vs. Qdrant

Os documentos da Santo Pegasus divergem sobre qual banco de dados vetorial o `ai-assistant-service`
usa:

- `santo-pegasus-arquitetura-microsservicos.md` diz **Pinecone**, em 4 pontos distintos:
  - linha 110 (diagrama textual): `[PostgreSQL] [PostgreSQL] [PostgreSQL] [Pinecone DB]`
  - linha 154 (tabela do catálogo): `| ai-assistant-service | 8087 | Assistência IA (RAG) | Pinecone (Vector) | Squad IA |`
  - linha 195: `Tecnologias: LangChain4j, OpenAI GPT-4o, Pinecone (Vector Store para conhecimento médico), Redis (Cache de respostas).`
  - linha 244: `Pinecone Cloud: \`knowledge_vectors\` (Vector store para RAG).`
- `santo-pegasus-guia-engenharia-backend.md` diz **Qdrant**, uma vez:
  - linha 134: `3. Vector Store: Uso de Qdrant em clusters de alta disponibilidade.`

**Não escolhi uma leitura.** Ambas as menções são lidas dos documentos, literalmente, sem
presunção. Já mapeado como ambiguidade legítima no `PLANO_EXECUCAO.md` (Etapa 9). Para o
conjunto de avaliação, isso deve virar um caso de **ambiguidade documentada**, não um fato-chave
de resposta única — a resposta correta do agente, se perguntado, é citar as duas fontes e admitir
a divergência, não escolher uma.

### 6.2 Prazo de notificação à ANPD: 48 horas úteis vs. 2 dias úteis

Ambas as menções estão no mesmo documento, `santo-pegasus-protocolo-incidentes-sre.md`, sobre o
mesmo evento (vazamento de dados/incidente SEV-1), com números escritos de formas diferentes:

- Seção 4 / SEV-1 (linha 55): `O SLA de notificação à ANPD é de 48 horas úteis, tornando a resposta SEV-1 uma operação com escrutínio legal.`
- Seção sobre conformidade jurídica (linha 167): `A lei exige notificação formal e fundamentada à Autoridade Nacional de Proteção de Dados (ANPD) em um prazo estritamente de 2 (dois) dias úteis após a ciência inequívoca do evento.`

"48 horas úteis" e "2 dias úteis" podem ou não ser a mesma coisa dependendo de como se conta hora
útil vs. dia útil — o documento não esclarece a equivalência, e as duas frases usam unidades
diferentes para o que parece ser o mesmo prazo legal. **Não escolhi uma leitura.** Registrado como
ambiguidade para a Etapa 4, não como fato de resposta única.

---

## 7. Fora de escopo (não existe em nenhum dos 14 documentos — deve produzir recusa)

**Seções 12 a 15 do índice de `santo-pegasus-arquitetura-microsservicos.md`** — o índice promete
"12. Mapa de Squads e Ownership", "13. Roadmap Técnico", "14. Architecture Decision Records
(ADRs)" e "15. Disposições Finais e Processo de Atualização", mas o corpo do documento termina na
Seção 11. Confirmado por extração completa das 12 páginas físicas do PDF (ver
`2026-08-12-etapa-2-conversao-13.md` e `2026-08-13-etapa-2-verificacao-ordem-e-extrator.md`) e
marcado no próprio `.md` em 13/08/2026 (ver `2026-08-13-etapa-2-secoes-ausentes.md`). Uma
pergunta sobre o conteúdo de qualquer uma dessas 4 seções deve produzir admissão de ausência, não
invenção.

**Faturamento anual do Mercado Central 24h** — busca por "faturamento anual", "faturamento
total" e "receita anual" nos 4 documentos do Mercado Central: **0 ocorrências**. O valor não
existe em nenhum dos documentos-fonte.

**Capital da França** — fora do domínio de todos os 14 documentos (busca por "frança": 0
ocorrências em todo o corpus). Caso de recusa trivial, não relacionado a nenhuma das 3 empresas.

**CNPJ da própria BimBam Buy / da própria Santo Pegasus Soluciones** — busca por `CNPJ` +
`BimBam` e `CNPJ` + `Pegasus` nos 14 documentos: **0 ocorrências**. Contraste útil: o CNPJ do
**Mercado Central 24h** (00.123.456/0001-99) **existe** e está documentado
(`mercado-central-regulamento-procedimentos-sop.md`, linha 7, ver §3) — a mesma pergunta tem
resposta para uma empresa e não para as outras duas.

**Pagamento em criptomoeda/Bitcoin na BimBam Buy** — a Seção 3 de
`bimbam-faq-metodos-pagamento.md` lista explicitamente os métodos aceitos (cartão de crédito,
cartão de débito, transferência/PIX, boleto, carteiras digitais, parcelamento) e criptomoeda não
está entre eles. Busca por "criptomoeda"/"bitcoin"/"cripto" nos 14 documentos: **0 ocorrências**
em qualquer contexto de pagamento ao cliente (as 4 ocorrências de "criptografia" encontradas nos
documentos da Santo Pegasus são sobre segurança de dados em repouso, assunto diferente — não
confundir na avaliação).
