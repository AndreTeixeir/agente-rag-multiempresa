# MANUAL DE ONBOARDING: BEM-VINDO À SANTO PEGASUS SOLUCIONES

## 1. BEM-VINDO À SANTO PEGASUS: CULTURA E VALORES

Seja muito bem-vindo(a) à Santo Pegasus Soluciones! Estamos entusiasmados por você ter aceitado o desafio de voar conosco. Na Pegasus, não construímos apenas software; criamos soluções escaláveis que impactam milhares de usuários, sempre com o foco na excelência técnica e na colaboração humana.

### Nossa História

A Santo Pegasus nasceu em 2018, na garagem de um sobrado no bairro da Vila Madalena, em São Paulo, a partir da inquietação de três fundadores — uma engenheira de dados, um arquiteto de software e uma product designer — que acreditavam que a tecnologia brasileira poderia competir de igual para igual com soluções internacionais. O nome "Pegasus" foi escolhido em homenagem ao cavalo alado da mitologia grega, símbolo de elevação e liberdade; o "Santo" veio de uma brincadeira interna nos primeiros meses da empresa, quando os fundadores diziam que só um "santo milagre" faria aquele MVP funcionar a tempo do primeiro grande cliente. O nome pegou, e a lenda interna se tornou parte da nossa identidade.

Nos primeiros dois anos, a empresa cresceu organicamente atendendo clientes de médio porte no setor de varejo e saúde, sempre com um time pequeno e multidisciplinar. Em 2020, com a aceleração da transformação digital, a Pegasus captou seu primeiro aporte de investimento anjo e iniciou a construção da plataforma "Agendio", nosso produto-carro-chefe de agendamento e gestão de atendimentos, que hoje atende milhares de estabelecimentos em todo o Brasil e começa sua expansão para a América Latina.

Hoje, em 2026, somos mais de 180 "Pegasos" (assim chamamos nossos colaboradores) distribuídos entre o Ninho físico em São Paulo e squads remotas em diversas cidades do Brasil. Continuamos com a mesma inquietação dos fundadores: a convicção de que tecnologia bem-feita, com pessoas bem tratadas, muda o jogo.

### Nossa Missão

Capacitar empresas através da tecnologia de ponta, simplificando processos complexos e entregando valor real de forma ágil.

### Nossa Visão

Ser reconhecida, até 2030, como a principal plataforma de agendamento, pagamentos e inteligência artificial aplicada para pequenos e médios negócios na América Latina.

### Nossos Valores (O "DNA Pegasus")

- Transparência Radical: Somos abertos sobre nossos sucessos e, principalmente, sobre nossos erros. O aprendizado vem da honestidade. Isso se reflete em nossos post-mortems sem culpados (blameless), em nossos all-hands mensais onde números reais (bons e ruins) são compartilhados, e na forma como líderes dão e recebem feedback.
- Qualidade sem Atalhos: Código bem escrito, testado e documentado não é um luxo, é o nosso padrão. Preferimos entregar um pouco mais tarde e com qualidade do que rápido e quebrado.
- Colaboração Acima do Ego: Aqui, a melhor ideia vence, não importa de quem ela venha — seja de um Pegaso Júnior no seu primeiro mês ou de um Staff Engineer com anos de empresa.
- Equilíbrio (Work-Life Balance): Valorizamos sua produtividade, mas respeitamos profundamente seu descanso e sua vida pessoal. Reuniões fora do horário comercial são desencorajadas e mensagens fora do expediente não exigem resposta imediata.
- Ownership (Dono do Problema): Esperamos que cada Pegaso trate os problemas da empresa como se fossem seus, buscando soluções e não apenas reportando obstáculos.
- Diversidade como Força: Acreditamos que times diversos (de gênero, raça, orientação, formação e vivência) constroem produtos melhores e mais inclusivos.

## 2. ESTRUTURA DE SQUADS E CHAPTERS

Na Santo Pegasus, utilizamos uma estrutura matricial para garantir agilidade na entrega e evolução constante das competências técnicas.

### Organograma Textual Simplificado

```
CEO
 VP de Engenharia
       Head of Engineering (Product)
          Squad Agendio Core
          Squad Pagamentos
          Squad IA/RAG
          Squad Plataforma
       Head of Chapters (Técnico)
          Chapter Back-end
          Chapter Front-end
          Chapter DevOps/SRE
          Chapter Data
       Head of People (RH)
           Recrutamento & Seleção
           Benefícios & Folha
           Cultura & Desenvolvimento
```

### Squads (Foco no Produto)

Você será alocado em uma Squad multidisciplinar. Cada Squad possui:

- 1 Product Owner (PO)
- 1 Scrum Master / Agile Facilitator
- Desenvolvedores (Backend, Frontend)
- 1 QA (Quality Assurance)
- 1 Product Designer (UX/UI)

Atualmente, temos quatro squads principais de produto:

**Squad Agendio Core**
Responsável pelo coração da plataforma: o motor de agendamentos, calendários, disponibilidade de horários e gestão de estabelecimentos. É a squad mais antiga da empresa e concentra o maior volume de tráfego. Trabalha intensamente com regras de negócio complexas (fusos horários, recorrência de agendamentos, bloqueios de agenda) e é uma ótima porta de entrada para quem quer entender profundamente o domínio de negócio da Pegasus.

**Squad Pagamentos**
Cuida de toda a jornada financeira: cobrança recorrente, split de pagamentos, conciliação bancária, emissão de notas fiscais e integração com gateways (Stripe, Pagar.me, PIX). É uma squad com altíssimo rigor de qualidade e segurança, dado o impacto financeiro direto de qualquer falha. Exige atenção especial a testes automatizados e conformidade com PCI-DSS.

**Squad IA/RAG**
A squad mais nova, criada em 2024, responsável pelo "Pegasus Assistant", nosso assistente virtual baseado em modelos de linguagem (LLMs) com arquitetura RAG (Retrieval-Augmented Generation) para responder dúvidas de clientes finais e auxiliar estabelecimentos na gestão da agenda via linguagem natural. Trabalha com pipelines de embeddings, vetores (pgvector) e orquestração de prompts.

**Squad Plataforma**
Squad "horizontal" que sustenta as demais: observabilidade, autenticação/autorização, gestão de tenants, billing interno e ferramentas de desenvolvedor (developer experience). Se você gosta de construir "ferramentas para quem constrói ferramentas", essa é a squad ideal.

### Chapters (Foco na Carreira e Técnica)

O Chapter é a sua "casa técnica". É onde você troca figurinhas com outros desenvolvedores da mesma disciplina, independentemente da Squad em que estão.

- Back-end Chapter: Focado em Java, arquitetura de microsserviços e performance.
- Front-end Chapter: Focado em ecossistema JavaScript/TypeScript e design system.
- DevOps Chapter: Responsável por Cloud (AWS), CI/CD e infraestrutura como código.
- Data Chapter: Focado em engenharia de dados, analytics e LGPD.

Os Chapters se reúnem quinzenalmente para "Refinamentos Técnicos" e mensalmente para "Tech Talks", onde um membro apresenta um tema de aprofundamento técnico ou um estudo de caso de um problema resolvido.

## 3. DIA 1: SETUP DE ACESSO

Seu primeiro dia será focado em garantir que você tenha as chaves para todas as nossas portas digitais. Abaixo, a tabela completa de acessos, prazos de liberação e responsáveis por cada solicitação.

| # | Acesso/Ferramenta | Prazo de Liberação | Solicitar a | Observação |
|---|---|---|---|---|
| 1 | E-mail corporativo (Google Workspace) | Antes do Dia 1 | TI (automático) | Já vem pronto na sua chegada |
| 2 | Slack | Dia 1 (manhã) | TI / Buddy | Convite automático via e-mail corporativo |
| 3 | Jira | Dia 1 (manhã) | Scrum Master da Squad | Acesso ao board específico da squad |
| 4 | Confluence | Dia 1 (manhã) | Scrum Master da Squad | Base de conhecimento |
| 5 | GitHub (org `santo-pegasus-dev`) | Dia 1 (até 12h) | Tech Lead da Squad | Login via SSO com e-mail corporativo |
| 6 | VPN (OpenVPN) | Dia 1 (até 17h) | TI (#help-ti) | Arquivo `.ovpn` enviado por e-mail |
| 7 | AWS (IAM Identity Center) | Dia 2 | DevOps Chapter | Acesso inicial somente leitura |
| 8 | Gestor de Senhas (1Password) | Dia 1 (obrigatório) | TI (#help-ti) | Cofre da Squad + cofre pessoal |
| 9 | Nexus (repositório interno Maven/NPM) | Dia 2 | Tech Lead | Necessário para builds internos |
| 10 | Datadog / Grafana (observabilidade) | Dia 3 a 5 | Chapter DevOps | Acesso liberado após treinamento básico |
| 11 | Figma (apenas Front-end/Design) | Dia 1 | Product Designer da Squad | Visualização de protótipos |
| 12 | Sistema de Ponto (Ahgora) | Antes do Dia 1 | RH | Apenas colaboradores CLT |
| 13 | Portal do Colaborador (RH) | Antes do Dia 1 | RH | Holerites, férias, benefícios |

> Importante: Se algum desses acessos não for liberado dentro do prazo, comunique imediatamente seu Buddy ou abra um chamado no canal `#help-ti`.

### Ferramentas de Comunicação e Gestão

1. Slack: Nossa sede oficial. Entre nos canais da sua Squad e nos canais gerais (#announcements, #tech-talks, #random).
2. Jira: Onde o trabalho acontece. Você receberá um convite para o board da sua Squad.
3. GitHub: Solicite acesso à organização `santo-pegasus-dev`. O acesso é via SSO com seu e-mail corporativo.
4. VPN (OpenVPN): Essencial para acessar nossos ambientes de staging e bancos de dados internos. O arquivo de configuração será enviado pelo time de TI.

### Cloud e Infra

- AWS: Você receberá acesso via IAM Identity Center. Verifique se consegue logar no Console AWS (permissões de leitura inicialmente).
- Confluence: Nossa base de conhecimento. Comece lendo a documentação técnica da sua Squad específica.

## 4. CONFIGURAÇÃO DO AMBIENTE BACK-END

Se você é um Pegasus Back-end Developer, siga os passos abaixo. O objetivo é que, ao final do Dia 2, você consiga rodar o projeto principal localmente.

**Passo 1: Instalar o SDKMAN e o Java**

Utilizamos o SDKMAN para gerenciar versões de Java e ferramentas relacionadas.

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk version
```

Instale o Amazon Corretto 17, nossa versão padrão:

```bash
sdk install java 17.0.9-amzn
sdk default java 17.0.9-amzn
java -version
```

**Passo 2: Instalar Maven**

```bash
sdk install maven 3.9.6
mvn -version
```

**Passo 3: Clonar o repositório**

```bash
git clone git@github.com:santo-pegasus-dev/agendio-core-service.git
cd agendio-core-service
```

> Substitua o nome do repositório pelo projeto principal da sua Squad, informado pelo seu Tech Lead.

**Passo 4: Instalar o Docker**

Instale o Docker Desktop (ou Colima, no macOS) e verifique:

```bash
docker --version
docker compose version
```

Suba os serviços de infraestrutura local (PostgreSQL/Redis):

```bash
docker compose -f docker-compose.local.yml up -d
docker ps
```

**Passo 5: Configurar as Variáveis de Ambiente**

Nunca versionamos arquivos `.env`. Solicite ao seu Tech Lead o arquivo `.env.example` atualizado, copie-o e preencha os valores:

```bash
cp .env.example .env
```

Lista de variáveis obrigatórias que você encontrará no `.env`:

| Variável | Descrição |
|---|---|
| `DB_HOST` | Host do banco PostgreSQL local |
| `DB_PORT` | Porta do banco (padrão 5432) |
| `DB_NAME` | Nome do banco de dados |
| `DB_USER` | Usuário do banco |
| `DB_PASSWORD` | Senha do banco (fornecida pelo Tech Lead via 1Password) |
| `REDIS_HOST` | Host do Redis local |
| `REDIS_PORT` | Porta do Redis (padrão 6379) |
| `JWT_SECRET` | Chave secreta para geração de tokens (ambiente local) |
| `AWS_REGION` | Região padrão da AWS (ex: `sa-east-1`) |
| `AWS_ACCESS_KEY_ID` | Chave de acesso (perfil de desenvolvimento) |
| `AWS_SECRET_ACCESS_KEY` | Chave secreta (perfil de desenvolvimento) |
| `NEXUS_USERNAME` | Usuário do repositório Nexus |
| `NEXUS_PASSWORD` | Senha/token do Nexus |
| `SPRING_PROFILES_ACTIVE` | Deve ser `local` para ambiente local |
| `SENTRY_DSN` | Endpoint de monitoramento de erros (opcional em local) |

**Passo 6: Configurar o Maven para o Nexus interno**

Verifique se o seu `~/.m2/settings.xml` aponta para o Nexus da Pegasus:

```xml
<servers>
 <server>
      <id>pegasus-nexus</id>
      <username>${env.NEXUS_USERNAME}</username>
      <password>${env.NEXUS_PASSWORD}</password>
 </server>
</servers>
```

**Passo 7: Rodar a aplicação**

```bash
mvn clean install -DskipTests
mvn spring-boot:run
```

Acesse `http://localhost:8080/actuator/health` e verifique se retorna `{"status":"UP"}`.

### Checklist de ferramentas Back-end

1. Java 17+: Utilizamos o Amazon Corretto 17 como padrão.
2. IDE: Recomendamos o IntelliJ IDEA (temos licenças Ultimate disponíveis).
3. Docker: Instale o Docker Desktop ou Colima. A maioria dos nossos projetos depende de `docker-compose` para subir bancos de dados (PostgreSQL/Redis) localmente.
4. Variáveis de Ambiente: Nunca versionamos arquivos `.env`. Solicite ao seu tech lead o arquivo `.env.example` atualizado e configure sua cópia local.
5. Maven: Utilizamos o Maven para gestão de dependências. Certifique-se de que o seu `settings.xml` aponta para o nosso Nexus interno se necessário.

## 5. CONFIGURAÇÃO DO AMBIENTE FRONT-END

Para os Pegasus Front-end Developers, o setup padrão é:

**Passo 1: Instalar o NVM (Node Version Manager)**

```bash
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.7/install.sh | bash
source ~/.bashrc
nvm --version
```

**Passo 2: Instalar a versão LTS do Node**

```bash
nvm install --lts
nvm use --lts
node -v
npm -v
```

**Passo 3: Clonar o repositório**

```bash
git clone git@github.com:santo-pegasus-dev/agendio-web-app.git
cd agendio-web-app
```

**Passo 4: Instalar as dependências**

```bash
npm install
```

**Passo 5: Configurar variáveis de ambiente**

```bash
cp .env.example .env.local
```

Preencha as variáveis principais (`VITE_API_BASE_URL`, `VITE_AUTH_DOMAIN`, `VITE_SENTRY_DSN`) com os valores fornecidos pelo Tech Lead da sua Squad.

**Passo 6: Rodar o projeto em modo de desenvolvimento**

```bash
npm run dev
```

Acesse `http://localhost:5173` e verifique se a aplicação carrega corretamente, exibindo a tela de login.

### Checklist de ferramentas Front-end

1. Node.js: Utilizamos a versão LTS mais recente. Recomendamos o uso do `nvm` (Node Version Manager).
2. IDE: O VS Code é o padrão de ouro aqui. Recomendamos as extensões: ESLint, Prettier, Tailwind CSS IntelliSense e GitLens.
3. Gerenciador de Pacotes: Utilizamos prioritariamente o `npm`.
4. Design System: Conheça o "Pegasus UI" via Storybook (link disponível no Confluence). Todas as interfaces devem seguir os componentes base.

## 6. IDEs: INTELLIJ IDEA E VS CODE

### IntelliJ IDEA (Back-end)

Todos os desenvolvedores Back-end têm direito a licença Ultimate, solicitada via ticket ao TI.
Plugins obrigatórios:

- Lombok Plugin: para suportar as anotações que utilizamos extensivamente.
- SonarLint: análise estática de código, alinhada às regras do SonarQube da empresa.
- CheckStyle-IDEA: valida o style guide de código Java da Pegasus.
- .env files support: realce de sintaxe para arquivos `.env`.
- Docker Plugin: gerenciamento de containers direto da IDE.
- GitToolBox: informações extras de Git na barra de status.

Configurações recomendadas: ativar "Reformat code" e "Optimize imports" antes de cada commit (Settings > Tools > Actions on Save).

### VS Code (Front-end)

Extensões obrigatórias:

- ESLint: validação de regras de lint em tempo real.
- Prettier – Code formatter: formatação automática ao salvar.
- Tailwind CSS IntelliSense: autocomplete de classes utilitárias.
- GitLens: histórico e blame de código inline.
- Error Lens: destaque visual de erros e warnings diretamente no editor.
- EditorConfig for VS Code: padronização de indentação e encoding.

Configuração recomendada no `settings.json`:

```json
{
    "editor.formatOnSave": true,
    "editor.defaultFormatter": "esbenp.prettier-vscode",
    "editor.codeActionsOnSave": {
        "source.fixAll.eslint": true
    }
}
```

## 7. FLUXO DE TRABALHO: GITFLOW E CODE REVIEW

Qualidade é inegociável. Seguimos um fluxo rigoroso para garantir a estabilidade do ambiente de produção.

### GitFlow Pegasus

- main: Reflete o código em produção.
- develop: Base para novas features.
- feature/PEG-[ID-DA-TASK]: Sua branch de trabalho.
- hotfix/: Correções críticas em produção.

### Exemplo Prático Completo de uma Feature

Vamos supor que você recebeu a task PEG-1345: "Adicionar campo de observações no agendamento".

1. Atualize sua branch develop local:

```bash
git checkout develop
git pull origin develop
```

2. Crie sua branch de feature:

```bash
git checkout -b feature/PEG-1345-campo-observacoes-agendamento
```

3. Desenvolva e faça commits pequenos e descritivos (padrão Conventional Commits):

```bash
git add src/main/java/com/pegasus/agendio/model/Agendamento.java
git commit -m "feat(PEG-1345): adiciona campo observacoes na entidade Agendamento"
git add src/main/java/com/pegasus/agendio/dto/AgendamentoDTO.java
git commit -m "feat(PEG-1345): inclui campo observacoes no DTO de resposta"
git add src/test/java/com/pegasus/agendio/AgendamentoServiceTest.java
git commit -m "test(PEG-1345): adiciona testes unitarios para campo observacoes"
```

4. Suba sua branch para o repositório remoto:

```bash
git push origin feature/PEG-1345-campo-observacoes-agendamento
```

5. Abra o Pull Request no GitHub, sempre apontando para `develop`, preenchendo o template (ver seção abaixo).

6. Aguarde as revisões, ajuste conforme comentários, e após 2 aprovações e CI verde, faça o merge (squash merge é o padrão da Pegasus):

```bash
Merge realizado via interface do GitHub, com opção
"Squash and merge"

```

7. Delete a branch local e remota após o merge:

```bash
git checkout develop
git pull origin develop
git branch -d feature/PEG-1345-campo-observacoes-agendamento
```

### Processo de Pull Request (PR)

1. Abra o PR apontando sempre para `develop`.
2. Preencha o template de PR (descrição, testes realizados, screenshots/vídeos se houver mudança visual).
3. A Regra de Ouro: Um PR só pode ser mergeado após, no mínimo, 2 aprovações (Approve) de outros desenvolvedores.
4. O CI (GitHub Actions) deve estar verde (testes passados, lint ok).

### Template Completo de Pull Request

```markdown
Descrição

Adiciona o campo "observações" à entidade Agendamento, permitindo que
o cliente inclua anotações livres no momento do agendamento.
Task relacionada

PEG-1345
Tipo de mudança

  • [x] Nova funcionalidade
  • [ ] Correção de bug
  • [ ] Refatoração
  • [ ] Documentação
Como testar

1. Suba o ambiente local com `docker compose up -d`
2. Rode `mvn spring-boot:run`
3. Faça um POST em `/api/agendamentos` incluindo o campo `observacoes`
4. Verifique se o campo é persistido e retornado corretamente
Testes realizados

  • [x] Testes unitários adicionados/atualizados
  • [x] Testado localmente
  • [x] Testado em ambiente Staging-Peg
Screenshots/Vídeos (se aplicável)

N/A (mudança apenas de back-end)
Checklist

      • [x] Meu código segue o style guide da Pegasus
      • [x] Realizei uma auto-revisão do meu código
      • [x] Comentei partes de lógica complexa
      • [x] Atualizei a documentação relevante (Confluence/Swagger)
      • [x] Não há warnings novos gerados pelo SonarLint
```

## 8. PRIMEIRAS TAREFAS: 30/60/90 DIAS

As expectativas variam conforme o seu nível de senioridade. Use esta seção como guia — converse sempre com seu Tech Lead para ajustar metas específicas da sua Squad.

### Nível Júnior

| Período | Expectativas |
|---|---|
| 30 dias | Ambiente configurado, primeiro commit de batismo mergeado, participação ativa em Dailies, entendimento básico do domínio da Squad. |
| 60 dias | Entrega de 2 a 3 tasks de complexidade baixa/média de forma independente, com pouca necessidade de retrabalho após code review. |
| 90 dias | Autonomia em tasks de complexidade média, começando a revisar PRs de outros juniores, participação em Refinamentos Técnicos. |

### Nível Pleno

| Período | Expectativas |
|---|---|
| 30 dias | Ambiente configurado rapidamente, entendimento sólido da arquitetura do produto, primeiras entregas de complexidade média. |
| 60 dias | Autonomia completa nas tasks da Squad, contribuição em decisões técnicas, revisão de PRs de outros membros. |
| 90 dias | Liderança técnica em pequenas iniciativas, participação em discussões de arquitetura, possível início de mentoria a juniores. |

### Nível Sênior

| Período | Expectativas |
|---|---|
| 30 dias | Diagnóstico crítico da arquitetura atual da Squad, identificação de gaps técnicos e de processo, entrega de pelo menos uma melhoria concreta. |
| 60 dias | Condução de decisões arquiteturais relevantes, mentoria ativa a plenos e juniores, participação em RFCs (Request for Comments) técnicos. |
| 90 dias | Responsabilidade por iniciativas estratégicas da Squad, representação do time em discussões entre Chapters, contribuição para roadmap técnico. |

## 9. SUPORTE E PEOPLE (RH): REGRAS E BENEFÍCIOS

A Santo Pegasus opera em conformidade com a legislação brasileira, oferecendo suporte tanto para colaboradores CLT quanto PJ.

### Benefícios Locais

- Vale-Refeição/Alimentação: Creditado mensalmente no cartão Caju (Multibenefícios), no valor definido em seu contrato, sempre até o 5º dia útil do mês.
- Vale-Transporte: Disponibilizado conforme necessidade (para dias de escritório em SP), solicitado via Portal do Colaborador com pelo menos 10 dias de antecedência.
- Plano de Saúde: Bradesco Saúde ou Amil (Top Nacional). Inclusão automática para titulares a partir do primeiro dia; dependentes sob consulta e mediante inclusão formal via RH em até 30 dias da contratação.
- Plano Odontológico: Incluso automaticamente junto ao plano de saúde, sem custo adicional para o titular.
- Gympass: Ativo para todos os colaboradores após o primeiro dia, com acesso via aplicativo próprio.
- PLR (Participação nos Lucros e Resultados): Distribuída anualmente, conforme acordo coletivo e atingimento de metas da empresa e da Squad. O valor de referência e as regras são comunicados em reunião específica de RH no início de cada ciclo, geralmente em janeiro.
- Auxílio Home Office: Valor mensal fixo para custos de internet e energia, creditado junto ao vale-alimentação.

### Gestão de Tempo e Férias

- Ponto (CLT): Registro via aplicativo Ahgora.
- Férias: Devem ser solicitadas via portal do RH com no mínimo 30 dias de antecedência, após alinhamento com o seu gestor de Squad.
- Reembolsos: Despesas com cursos, certificações ou hardware devem ser aprovadas previamente e enviadas via relatório de despesas até o dia 20 de cada mês.

### Política de Férias (CLT)

Conforme a legislação brasileira, todo colaborador CLT tem direito a 30 dias corridos de férias após completar 12 meses de trabalho (período aquisitivo). Na Santo Pegasus:

- As férias podem ser fracionadas em até 3 períodos, sendo que um deles não pode ser inferior a 14 dias corridos, e os demais não podem ser inferiores a 5 dias corridos cada.
- É obrigatório o gozo de pelo menos um período de férias dentro dos 12 meses subsequentes ao período aquisitivo, evitando o "vencimento" das férias (o que gera pagamento em dobro, conforme a CLT).
- Solicitações de fracionamento devem ser aprovadas pelo gestor da Squad e formalizadas no Portal do Colaborador.
- O pagamento do adicional de 1/3 constitucional é feito junto com o salário do mês anterior ao início das férias.

### Política de Home Office e Trabalho Híbrido

A Santo Pegasus adota o modelo híbrido flexível como padrão:

- Colaboradores de squads de produto (Agendio Core, Pagamentos, IA/RAG, Plataforma) têm liberdade para escolher seus dias de comparecimento ao Ninho, com recomendação de pelo menos 1 dia por semana presencial para fortalecer a colaboração e integração entre squads.
- Eventos específicos (Sprint Planning trimestral, Tech Talks, integrações de novos Pegasos) podem exigir presença obrigatória, sempre comunicada com antecedência mínima de 7 dias.
- Colaboradores 100% remotos (fora da Grande São Paulo) seguem o mesmo padrão de entregas e participam normalmente de todos os rituais via videoconferência.
- É de responsabilidade do colaborador garantir conexão de internet estável e um ambiente de trabalho adequado (ergonomia, silêncio para reuniões).

## 10. SEGURANÇA DESDE O PRIMEIRO DIA

Segurança da informação é responsabilidade de todos os Pegasos, não apenas do time de TI/Segurança.

### Gestor de Senhas Corporativo

- Todo colaborador deve configurar o 1Password já no Dia 1. Nenhuma senha de sistemas internos, banco de dados ou serviços de terceiros deve ser armazenada em blocos de notas, planilhas ou aplicativos pessoais.
- Cada Squad possui um "cofre" (vault) compartilhado no 1Password com credenciais de uso comum (ex: acessos a ambientes de staging).
- Credenciais individuais (ex: chaves de API pessoais) devem ficar no cofre privado do colaborador.

### Política de Dispositivos

- Notebooks corporativos são fornecidos com criptografia de disco ativada (BitLocker/FileVault) e antivírus corporativo instalado por padrão.
- É proibido o uso de dispositivos pessoais para acessar sistemas de produção ou dados sensíveis de clientes.
- Toda perda ou roubo de equipamento deve ser reportada imediatamente ao canal `#help-ti`, para bloqueio remoto dos acessos.
- Autenticação multifator (MFA) é obrigatória em todos os sistemas críticos (AWS, GitHub, Google Workspace, 1Password).
- Colaboradores devem manter o sistema operacional e aplicativos sempre atualizados, aplicando patches de segurança assim que notificados pelo TI.

### Boas Práticas de Segurança de Código

- Nunca versionar segredos, chaves ou senhas em repositórios Git (mesmo privados).
- Utilizar sempre o `.gitignore` padrão da Pegasus, que já bloqueia arquivos sensíveis comuns.
- Reportar qualquer vulnerabilidade encontrada via canal `#security-reports`, nunca divulgando publicamente antes da correção.

## 11. CHECKLIST DA PRIMEIRA SEMANA

- [ ] Dia 1: Acessar Slack, E-mail, Jira e GitHub. (Validado por: Buddy)
- [ ] Dia 1: Configurar o gestor de senhas 1Password. (Validado por: TI)
- [ ] Dia 1: Participar da reunião de boas-vindas com o RH. (Validado por: RH)
- [ ] Dia 1: Receber e conferir o notebook corporativo e demais equipamentos. (Validado por: TI)
- [ ] Dia 2: Realizar o setup da máquina e rodar o projeto localmente. (Validado por: Tech Lead)
- [ ] Dia 2: Reunião 1:1 com o seu Tech Lead para entender as metas da Squad. (Validado por: Tech Lead)
- [ ] Dia 2: Obter acesso à AWS (IAM Identity Center) em modo leitura. (Validado por: Chapter DevOps)
- [ ] Dia 3: Ler a documentação de arquitetura no Confluence. (Validado por: Buddy)
- [ ] Dia 3: Configurar IDE (IntelliJ ou VS Code) com todos os plugins/extensões obrigatórios. (Validado por: você mesmo, checklist da Seção 6)
- [ ] Dia 4: Realizar o primeiro "Commit de Batismo" (geralmente uma task de baixa complexidade ou correção de documentação). (Validado por: Tech Lead via aprovação do PR)
- [ ] Dia 5: Participar da primeira Daily e Retro da Squad. (Validado por: Scrum Master)
- [ ] Dia 5: Reunião de fechamento de semana com o Buddy para tirar dúvidas gerais. (Validado por: Buddy)

## 12. CONTATOS ÚTEIS

| Área/Squad | Nome/Função | Canal Slack |
|---|---|---|
| Tech Lead — Agendio Core | Responsável técnico da squad | `#squad-agendio-core` |
| Tech Lead — Pagamentos | Responsável técnico da squad | `#squad-pagamentos` |
| Tech Lead — IA/RAG | Responsável técnico da squad | `#squad-ia-rag` |
| Tech Lead — Plataforma | Responsável técnico da squad | `#squad-plataforma` |
| Chapter Back-end | Líder do chapter | `#chapter-backend` |
| Chapter Front-end | Líder do chapter | `#chapter-frontend` |
| Chapter DevOps/SRE | Líder do chapter | `#chapter-devops` |
| Chapter Data | Líder do chapter | `#chapter-data` |
| RH / People | Time de People | `#help-rh` |
| TI / Suporte Técnico | Time de Infraestrutura | `#help-ti` |
| Segurança da Informação | Time de Segurança | `#security-reports` |
| Seu Buddy | Definido no convite de boas-vindas | Mensagem direta (DM) |

## 13. DICIONÁRIO DE TERMOS INTERNOS DA PEGASUS

Para você não ficar perdido nas reuniões, aqui estão os termos mais comuns:

- "Pegaso": Como chamamos carinhosamente os colaboradores da empresa.
- "Voo": Lançamento de uma grande feature ou nova versão do produto. ("O voo do módulo fiscal será amanhã").
- "Ninho": Nosso escritório físico em São Paulo.
- "Staging-Peg": Nosso ambiente de homologação que espelha produção.
- "Refinamento Técnico": Reunião onde o Chapter avalia a viabilidade de uma solução antes dela ir para o board da Squad.
- "Debate de Código": Uma sessão de pair programming ou discussão técnica intensa.
- "Commit de Batismo": O primeiro commit mergeado por um novo Pegaso.
- "Buddy": Colaborador designado para acompanhar o novo Pegaso nos primeiros 15 dias.
- "All Hands": Reunião geral mensal com todos os Pegasos, onde resultados e novidades são compartilhados.
- "PEG-[número]": Prefixo padrão de todas as tasks no Jira.
- "RFC" (Request for Comments): Documento técnico proposto por alguém do time para discussão aberta antes de decisões arquiteturais importantes.
- "War Room": Canal/sala de guerra criado durante incidentes críticos em produção.
- "Pegasus Assistant": Nosso assistente virtual de IA para clientes finais, desenvolvido pela Squad IA/RAG.
- "Agendio": Nome do nosso produto principal de agendamento e gestão de atendimentos.
- "DoR" (Definition of Ready): Critérios que uma task precisa cumprir antes de entrar no Sprint.
- "DoD" (Definition of Done): Critérios que uma task precisa cumprir para ser considerada finalizada.
- "Squash Merge": Estratégia padrão de merge da Pegasus, que condensa todos os commits de uma feature em um único commit na branch de destino.
- "Blameless Post-Mortem": Análise de incidentes sem apontar culpados, focada em processos e aprendizado.
- "Tech Talk": Apresentação técnica mensal aberta a todos os Chapters.
- "Sprint Zero": Primeira semana de um novo Pegaso, focada em onboarding e ambientação, sem entregas de negócio esperadas.
- "1:1" (One-on-one): Reunião individual periódica entre colaborador e gestor.
- "Cofre" (1Password): Conjunto de credenciais compartilhadas por Squad ou de uso pessoal.
- "Sede Digital": Referência carinhosa ao Slack como ambiente central de comunicação.

## 14. PERGUNTAS FREQUENTES DO ONBOARDING

**Não recebi um dos acessos dentro do prazo. O que faço?**
Abra um chamado no canal `#help-ti` informando qual acesso está pendente e há quanto tempo. Se não houver resposta em 24h, avise seu Buddy ou Tech Lead.

**Posso escolher entre IntelliJ e VS Code mesmo sendo Back-end?**
O padrão recomendado para Back-end é o IntelliJ IDEA por conta da integração nativa com Java/Maven/Spring, mas não há bloqueio técnico ao uso de outra IDE, desde que você garanta a aderência aos plugins de qualidade (SonarLint, CheckStyle).

**Como funciona o pagamento do vale-alimentação no mês de contratação?**
O valor é calculado proporcionalmente aos dias trabalhados no mês de admissão e creditado junto com o próximo ciclo normal do cartão Caju.

**Posso emendar as férias fracionadas com feriados?**
Sim, desde que respeitados os períodos mínimos legais (um período de ao menos 14 dias e os demais de ao menos 5 dias) e aprovação do gestor da Squad.

**Sou PJ. Tenho direito aos mesmos benefícios de um CLT?**
Colaboradores PJ têm acesso a benefícios equivalentes conforme definido em contrato específico (ex: vale-alimentação e plano de saúde geralmente inclusos), mas regras de férias e ponto seguem a legislação de prestação de serviços, não a CLT. Consulte seu contrato ou o RH para detalhes.

**Quantas aprovações preciso para mergear um PR?**
No mínimo 2 aprovações de outros desenvolvedores, além do CI verde (testes e lint aprovados).

**Meu computador apresentou um problema de segurança (perda, roubo, malware). O que faço?**
Comunique imediatamente o canal `#help-ti` para bloqueio remoto dos acessos corporativos, independentemente do horário.

**Existe alguma flexibilidade na exigência de 1 dia presencial por semana?**
Sim. A recomendação é flexível e pode ser ajustada com o gestor da Squad conforme necessidades específicas do time e do momento de projeto.

Dúvidas? Procure seu Buddy (o desenvolvedor designado para te acompanhar nos primeiros 15 dias) ou chame o time de People no canal #help-rh.

Vamos voar alto juntos!
