# Fontes dos documentos — proveniência dos 14 PDFs

Os 14 PDFs em `src/main/resources/documentos/pdf/` foram copiados de
`~/Desktop/Projetos/alura-ia/` (projeto separado, fora deste repositório) em 2026-08-12,
com renomeação para a convenção deste projeto: minúsculas, sem acento, sem espaço, separado
por hífen, prefixado pela empresa (`bimbam-`, `mercado-central-`, `santo-pegasus-`). O prefixo
alimenta o metadado de empresa usado no chunking/retrieval multi-tenant.

Verificação de integridade na cópia: soma de bytes dos 14 arquivos = 6.537.906, contagem = 14,
ambos batendo exatamente com o inventário de origem. Nenhum outro arquivo entrou no diretório.

## Origem técnica dos PDFs

Todos os 14 PDFs foram gerados por **jsPDF 4.2.1** (`Producer: jsPDF 4.2.1`, confirmado via
`pdfinfo`). Por serem gerados programaticamente a partir de texto, têm camada de texto nativa
— **dispensam OCR** para extração de conteúdo.

## Tabela de proveniência

| Nome no repositório | Nome original | Empresa | Páginas | Tamanho (bytes) | Data da cópia |
|---|---|---|---|---|---|
| `bimbam-prazos-custos-envio.pdf` | `Guia de Prazos e Custos de Envio da BimBam Buy.pdf` | BimBam Buy | 11 | 273.134 | 2026-08-12 |
| `bimbam-garantia-produtos.pdf` | `Manual de Garantia de Produtos da BimBam Buy.pdf` | BimBam Buy | 10 | 256.505 | 2026-08-12 |
| `bimbam-faq-metodos-pagamento.pdf` | `Perguntas Frequentes sobre Métodos de\nPagamento da BimBam Buy.pdf` | BimBam Buy | 10 | 265.825 | 2026-08-12 |
| `bimbam-reembolsos-devolucoes.pdf` | `Política de Reembolsos e Devoluções da BimBam\nBuy.pdf` | BimBam Buy | 14 | 300.821 | 2026-08-12 |
| `bimbam-programa-afiliados.pdf` | `Programa de Afiliados da BimBam Buy.pdf` | BimBam Buy | 10 | 261.649 | 2026-08-12 |
| `mercado-central-fornecedores-compras.pdf` | `MANUAL DE FORNECEDORES E POLÍTICA DE\nCOMPRAS.pdf` | Mercado Central 24h | 17 | 509.939 | 2026-08-12 |
| `mercado-central-faq.pdf` | `MANUAL DE PERGUNTAS FREQUENTES (FAQ) .pdf` *(espaço antes de `.pdf` no original)* | Mercado Central 24h | 15 | 345.263 | 2026-08-12 |
| `mercado-central-atendimento-trocas-devolucoes.pdf` | `POLÍTICA INTEGRADA DE ATENDIMENTO, TROCAS,\nDEVOLUÇÕES E PRIVACIDADE.pdf` | Mercado Central 24h | 16 | 463.963 | 2026-08-12 |
| `mercado-central-regulamento-procedimentos-sop.pdf` | `REGULAMENTO INTERNO E MANUAL DE\nPROCEDIMENTOS OPERACIONAIS (SOP).pdf` | Mercado Central 24h | 17 | 525.632 | 2026-08-12 |
| `santo-pegasus-arquitetura-microsservicos.pdf` | `Arquitetura de Microsserviços e Mapa de Domínios.pdf` | Santo Pegasus | 12 | 426.931 | 2026-08-12 |
| `santo-pegasus-guia-engenharia-backend.pdf` | `Guia Oficial de Engenharia Back-end — Santo.pdf` | Santo Pegasus | 7 | 244.845 | 2026-08-12 |
| `santo-pegasus-guia-engenharia-frontend.pdf` | `Guia Oficial de Engenharia Front-end — Santo\nPegasus Soluciones.pdf` | Santo Pegasus | 26 | 411.057 | 2026-08-12 |
| `santo-pegasus-manual-onboarding.pdf` | `MANUAL DE ONBOARDING: BEM-VINDO À SANTO.pdf` *(dois-pontos no original)* | Santo Pegasus | 19 | 488.865 | 2026-08-12 |
| `santo-pegasus-protocolo-incidentes-sre.pdf` | `Protocolo Integral e Extenso de Resposta a\nIncidentes e Engenharia de Confiabilidade (SRE) —\nSanto Pegasus Soluciones.pdf` | Santo Pegasus | 12 | 1.763.477 | 2026-08-12 |

`\n` na coluna "Nome original" representa quebra de linha literal dentro do nome do arquivo de
origem (confirmado via inspeção de bytes, não formatação de terminal).

## Observações registradas

- **`santo-pegasus-guia-engenharia-backend.pdf`**: nome original truncado ("— Santo" em vez de
  "— Santo Pegasus Soluciones", padrão dos demais arquivos da mesma empresa). Investigado por
  suspeita de truncagem de conteúdo (7 páginas / ~1.492 palavras contra 26 páginas do guia de
  Front-end da mesma empresa). Extração de texto via `pdftotext -layout` mostra estrutura de
  seções completa (1 a 11.2) terminando em frase completa, seguida de aviso de confidencialidade
  que aparece uma única vez no arquivo (não é rodapé repetido por página — 7 marcas de página
  para 7 páginas). Sem evidência de corte no meio de frase ou seção; tratado como documento
  íntegro, apenas mais conciso que os demais.
- **`santo-pegasus-protocolo-incidentes-sre.pdf`**: `pdftotext` emite aviso não-fatal do
  poppler ao processar o arquivo — `Syntax Error (318925): Bad block header in flate stream`.
  O aviso não impede a extração (exit code 0, texto extraído normalmente). Registrado aqui para
  não ser confundido com corrupção do arquivo em etapas futuras.
