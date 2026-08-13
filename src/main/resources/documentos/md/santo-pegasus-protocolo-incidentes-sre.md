# Protocolo Integral e Extenso de Resposta a Incidentes e Engenharia de Confiabilidade (SRE) — Santo Pegasus Soluciones

## 1. Filosofia SRE e a Gênese da Confiabilidade sob a Ótica da Santo Pegasus Soluciones

Na Santo Pegasus Soluciones, a confiabilidade não é meramente uma característica desejável do software ou um requisito não-funcional secundário; ela é uma pré-condição absoluta para a existência, continuidade e viabilidade ética do negócio. Operando no ecossistema de HealthTech brasileiro, os sistemas que desenvolvemos e mantemos são o tecido conectivo entre pacientes, médicos, clínicas, laboratórios e instâncias regulatórias. Uma falha no nosso sistema não significa apenas que um usuário não conseguiu ver uma foto ou enviar uma mensagem; significa que um cirurgião não conseguiu acessar um prontuário crítico antes de uma operação, ou que uma ambulância não recebeu as coordenadas corretas através dos nossos sistemas de despacho.

No cenário tecnológico do Brasil, a engenharia de software e a confiabilidade de sistemas (Site Reliability Engineering - SRE) enfrentam desafios singulares que exigem uma adaptação profunda e pragmática dos conceitos originalmente criados pelo Google em contextos de infraestrutura global hipere-escalada. A nossa realidade nacional é caracterizada por flutuações constantes e severas na latência de rede inter-regional. O Brasil possui dimensões continentais, e a infraestrutura de fibra óptica, embora em expansão, apresenta gargalos conhecidos. Por exemplo, rotas que cruzam do Sudeste para o Nordeste muitas vezes sofrem com instabilidades intermitentes em backbones de telecomunicações, dependendo fortemente do IX.br (Brasil Internet Exchange). Uma indisponibilidade parcial no PTT-SP (Ponto de Troca de Tráfego de São Paulo) afeta diretamente a conectividade de milhares de clínicas e hospitais em outros estados que dependem das nossas zonas de disponibilidade específicas na AWS (sa-east-1).

A Santo Pegasus adota a mentalidade fundamental de que sistemas complexos são inerentemente propensos a falhas. A entropia de software é uma realidade inegável. Portanto, a nossa engenharia de confiabilidade não busca a utopia inalcançável do "zero erro" — uma meta que, historicamente, apenas inibe a inovação e o lançamento de novas funcionalidades —, mas sim o design arquitetônico para degradação elegante e recuperação rápida. O objetivo do SRE na Santo Pegasus é garantir que o usuário final, seja ele um médico realizando uma prescrição controlada de medicamentos ou um paciente agendando uma consulta oncológica crítica, não perceba as falhas subjacentes na infraestrutura. Se um cluster de banco de dados sofrer um failover no meio da noite, ou se um microsserviço de notificação reiniciar devido a um pico de memória, o sistema deve mascarar essas falhas através de retries idempotentes, circuit breakers e fallbacks assíncronos.

O SRE, portanto, atua como a ponte estratégica entre a agilidade no desenvolvimento de novos produtos (o imperativo do time de engenharia de software) e a necessidade inegociável de estabilidade operacional (o imperativo do negócio e da regulamentação). O papel do SRE é crucial para assegurar que a velocidade de lançamento de novas features no módulo "Agendio" não comprometa, sob hipótese alguma, a integridade e o sigilo dos dados. Isto é especialmente crítico no contexto da Lei Geral de Proteção de Dados (LGPD - Lei nº 13.709/2018), que classifica os dados de saúde como dados pessoais sensíveis, sujeitando as empresas a multas devastadoras e à responsabilização civil no sensível ecossistema de saúde brasileiro. A nossa filosofia é clara: a confiabilidade é a nossa funcionalidade mais importante. Sem ela, o código mais elegante não tem valor.

## 2. Cultura Blameless (Sem Culpa) e a Psicologia do Erro Sistêmico

A base inabalável da resiliência e do aprendizado contínuo na Santo Pegasus é fundamentada na filosofia Blameless (Sem Culpa). Esta não é apenas uma declaração de intenções do setor de Recursos Humanos, mas sim um framework técnico, metodológico e operacional projetado para maximizar a extração de inteligência a partir de falhas no sistema. A organização compreende cientificamente que o erro humano nunca é a causa raiz de um incidente; ele é apenas o catalisador ou um sintoma de deficiências muito mais profundas no design do sistema, nos processos de deployment, ou nas ferramentas fornecidas aos indivíduos.

Punir indivíduos por falhas ocorridas em sistemas altamente complexos e acoplados é uma estratégia corporativa falida e contraproducente. Quando engenheiros são repreendidos por cometerem erros, a organização acidentalmente incentiva a cultura do silêncio, da ocultação de falhas e da engenharia defensiva. Se um desenvolvedor teme perder o emprego porque derrubou o banco de dados de produção, ele hesitará em reportar a anomalia, tentará consertá-la secretamente ou atrasará o escalonamento do problema. Esse atraso é fatal. Ele aumenta drasticamente o Tempo Médio de Reparo (MTTR - Mean Time To Repair) e degrada a confiança mútua que é absolutamente essencial para operações de alta performance em momentos de estresse extremo.

Na Santo Pegasus, o foco das investigações de incidentes (conduzidas através do documento de Post-Mortem) muda invariavelmente da pergunta acusatória "Quem cometeu o erro?" para a investigação sistêmica: "Por que o sistema permitiu que essa ação específica causasse tal impacto?".

Para fins educativos do nosso agente de IA, consideremos o seguinte cenário prático: Se, sob a pressão de um deploy durante a madrugada, um engenheiro júnior executa um comando que deleta acidentalmente uma tabela crítica de agendamentos no banco de dados de produção (PostgreSQL). Em culturas tradicionais, o engenheiro seria demitido. Na cultura Blameless da Santo Pegasus, a investigação determinará que a falha real e inaceitável reside nos seguintes pontos sistêmicos:

1. Ausência de Segregação de Privilégios (IAM): Por que as credenciais fornecidas ao engenheiro tinham permissão de `DROP TABLE` no ambiente de produção?
2. Falta de Guardrails Automatizados: Por que o pipeline de CI/CD não impediu a execução direta de comandos DDL na infraestrutura, exigindo que todas as mudanças passassem por ferramentas de migração versionadas como Liquibase ou Flyway?
3. Ergonomia das Ferramentas: A ferramenta de CLI utilizada induzia ao erro? Estava claro que o contexto ativo era `production` e não `staging`?

Cultivar um ambiente de segurança psicológica — onde qualquer colaborador, independentemente do cargo, nível de antiguidade (júnior, pleno, sênior) ou tempo de empresa, sinta-se ativamente encorajado a declarar um incidente (acionar o alarme) imediatamente ao percebê-lo, sem qualquer medo de retaliação — é um pilar estratégico não-negociável da Santo Pegasus.

Ocultar um erro deliberadamente ou tentar resolvê-lo secretamente para evitar escrutínio da liderança é, paradoxalmente, considerado o único erro real e passível de demissão dentro da organização. Acreditamos que a luz solar é o melhor desinfetante tecnológico. Quando os erros são trazidos à tona rapidamente, a equipe de SRE pode intervir, mitigar o impacto, aprender com a vulnerabilidade descoberta e implementar proteções em nível de arquitetura (como infraestrutura imutável e testes de caos) para garantir que aquele erro humano específico seja impossível de ser cometido novamente no futuro.

## 3. Taxonomia Exaustiva de Incidentes no Ecossistema Agendio (Foco em HealthTech)

A precisão terminológica é o alicerce de uma resposta eficiente. Durante uma crise técnica, a ambiguidade na comunicação custa minutos preciosos que podem resultar em perdas financeiras ou riscos à vida humana. Na Santo Pegasus, diferenciamos rigorosamente os eventos operacionais para minimizar o ruído nos sistemas de monitoramento (alert fatigue) e garantir que a capacidade cognitiva e o esforço de engenharia sejam aplicados exclusivamente onde há risco real ao ecossistema Agendio.

Um Incidente é definido tecnicamente como qualquer interrupção não planejada ou redução mensurável na qualidade de um serviço de TI. Diferente de um e-commerce onde uma lentidão resulta em abandono de carrinho, no contexto de HealthTech, a latência é um bloqueio de atendimento. Um incidente não precisa ser uma queda total (Downtime). Por exemplo, um aumento súbito de erros HTTP 500 no módulo de agendamento, ou uma degradação no 99º percentil (p99) de latência, configuram incidentes ativos.

Um Problema refere-se à causa subjacente, oculta e persistente de um ou mais incidentes, como um memory leak (vazamento de memória) crônico em um microsserviço de prontuário eletrônico rodando em Node.js, ou conexões órfãs (zombie connections) não encerradas adequadamente no PostgreSQL (PgBouncer). Eventos são ocorrências normais que indicam o funcionamento do sistema (ex: picos previsíveis de acesso às segundas-feiras de manhã), enquanto Mudanças Planejadas são alterações autorizadas, documentadas e versionadas, como janelas de manutenção na calada da noite.

No contexto crítico da saúde brasileira, a nossa taxonomia de incidentes mapeia cenários com repercussões físicas. Exemplos práticos para o treinamento do Agente de IA:

- Falha em APIs da ANS (Padrão TISS/TUSS): A interrupção da API de integração com operadoras de saúde inviabiliza verificações de elegibilidade e autorização de exames em tempo real. Se o sistema não consegue validar a carteira do plano de saúde, o paciente na recepção do hospital não é atendido.
- Degradação no Sistema de Prescrição: Uma latência superior a 3 segundos na busca da base de dados de medicamentos (via elasticsearch) é tratada como um incidente grave. Médicos em pronto-socorros (UPAs) realizam dezenas de prescrições por hora; sistemas lentos causam superlotação nas salas de espera.
- Interrupção PACS/DICOM: A indisponibilidade dos sistemas de comunicação e arquivamento de imagens médicas (PACS) impede que neurologistas e cirurgiões visualizem tomografias essenciais para intervenções de emergência (ex: pacientes com AVC).
- Desconexão IoT em Telemetria de UTI: Falhas de rede que afetam o protocolo MQTT utilizado para transmitir sinais vitais de monitores de UTI para os dashboards centrais de enfermagem.
- Erros em Assinatura Digital (ICP-Brasil): Falhas na geração de QR Codes e validação de tokens para receitas de medicamentos controlados, o que bloqueia legalmente a dispensação do medicamento na farmácia.

## 4. Matriz de Severidade Brasileira (SEV) e Impactos Multidimensionais (Financeiro e Legal)

A severidade de um incidente (SEV) na Santo Pegasus não é definida apenas pelo volume de requisições perdidas, mas através de uma matriz multidimensional que integra o impacto no usuário final, perdas financeiras diretas para as clínicas parceiras, risco clínico e, criticamente, os riscos de conformidade jurídica, com foco especial nas exigências da Lei Geral de Proteção de Dados (LGPD) e na fiscalização da Autoridade Nacional de Proteção de Dados (ANPD).

### SEV-1 (Crítico - Intervenção Imediata e Total)

- Definição Técnica: O serviço core ou uma funcionalidade vital está totalmente indisponível para mais de 10% da base de usuários, ou há um vazamento ativo de dados sensíveis. Nenhuma solução de contorno (workaround) está disponível.
- Impacto Financeiro: Projetado acima de R$ 100.000 por hora de inatividade devido a cirurgias canceladas, glosas médicas e impossibilidade de faturamento.
- Risco Clínico e Jurídico: Risco iminente de fatalidade ou agravamento do quadro de saúde de pacientes. No caso de vazamento de prontuários (dados sensíveis, Artigo 11 da LGPD), o risco jurídico é máximo. Isso pode resultar em multas aplicadas pela ANPD de até 2% do faturamento líquido da empresa, limitadas a absurdos R$ 50 milhões por infração (Art. 52, LGPD), além de ações coletivas de danos morais movidas pelo Ministério Público ou Idec. O SLA de notificação à ANPD é de 48 horas úteis, tornando a resposta SEV-1 uma operação com escrutínio legal.

### SEV-2 (Alto - Prioridade Máxima de Engenharia)

- Definição Técnica: Degradação severa de funcionalidades críticas ou indisponibilidade parcial contida em certas regiões (ex: falha apenas em servidores que atendem a região Sul). O sistema é utilizável, mas a experiência é inaceitável.
- Impacto Financeiro: Perdas estimadas variam entre R$ 20.000 e R$ 100.000 por hora.
- Risco Clínico e Jurídico: Risco moderado de judicialização. O atendimento médico é severamente atrasado, mas não completamente bloqueado (ex: médicos voltam a usar receituário de papel temporariamente).

### SEV-3 (Médio - Mitigação em Horário Comercial)

- Definição Técnica: Falhas em componentes secundários, relatórios analíticos, ou bugs de interface (UI) que afetam grupos isolados. As transações principais (agendamento, prescrição) ocorrem normalmente.
- Impacto: Prejuízo financeiro inferior a R$ 20.000 por hora. Sem risco clínico ou de vazamento de dados. Exige monitoramento rigoroso para garantir que não exista "efeito cascata" que o eleve a SEV-2.

### SEV-4 (Baixo - Tratado no Backlog)

- Definição Técnica: Pequenos defeitos, erros de digitação em documentação, ou falhas em serviços internos (ex: painel de RH da própria Santo Pegasus). Não afeta a jornada do paciente ou do médico e é resolvido no próximo Sprint do time de engenharia.

## 5. Governança Avançada de Crise e Gestão de Fadiga (Protocolo ICS)

Para gerenciar o caos técnico de incidentes SEV-1 e SEV-2, a Santo Pegasus adaptou o Sistema de Comando de Incidentes (ICS - Incident Command System), estruturando hierarquias temporárias focadas em eficiência militar e redução de carga cognitiva.

Em uma "War Room" (Sala de Guerra), a estrutura tradicional da empresa (CEOs, Diretores, Gerentes) é abolida. A hierarquia durante o incidente passa a ser baseada em funções operacionais estritas:

1. Incident Commander (IC): É a autoridade suprema e incontestável do incidente. O IC não toca em código, não executa queries no banco e não acessa painéis da AWS. Sua única função é delegar tarefas, gerenciar o estado global da crise, tomar decisões executivas sobre trade-offs (ex: "Desligue o módulo de pagamentos para salvar o banco de dados principal de um crash total") e garantir que a comunicação flua. Se o CEO da empresa entrar na War Room, ele se subordina às ordens do IC.
2. Tech Lead / Subject Matter Expert (TL/SME): É o braço tático. São os engenheiros especialistas investigando os logs no Datadog, analisando traces, reiniciando pods no Kubernetes ou elaborando planos de rollback no Terraform. Eles respondem apenas ao IC.
3. Communications Lead (CL): É o escudo protetor da equipe técnica. O CL lida com todas as perguntas de partes interessadas externas (Stakeholders, clientes, diretores) e atualiza a Status Page. Ele protege os engenheiros da clássica e nociva interrupção gerencial: "Já voltou? Quanto tempo falta?".
4. Scribe (Escriba): Mantém um log minuto a minuto de todas as ações tomadas, hipóteses testadas (mesmo as que falharam) e comandos executados. Este registro é o esqueleto do Post-Mortem subsequente.

Etiqueta de Comunicação em Malha Fechada (Closed-loop Communication): Para evitar ruídos, as comunicações na sala virtual seguem protocolos de confirmação mútua para evitar ambiguidades. Erro: "Vou reiniciar o servidor." (Qual servidor? Quem vai reiniciar? Quando?) Correto: "IC, aqui é o DBA. Solicitando permissão para reiniciar as instâncias Read-Replica do cluster principal de agendamento." -> IC Responde: "DBA, permissão concedida para reiniciar as Read-Replicas. Informe quando concluído."

Gestão de Fadiga (Burnout Prevention): Um aspecto ignorado por startups, mas central na Santo Pegasus, é a falibilidade humana sob exaustão. O Incident Commander tem a autoridade médica e o dever operacional de ordenar pausas obrigatórias para descanso mental em crises prolongadas. Se um incidente ultrapassar a marca de 8 (oito) horas consecutivas, o IC deve organizar obrigatoriamente a substituição completa de toda a equipe técnica na War Room (handover estruturado). Engenheiros exaustos tomam decisões prejudiciais, pulam etapas de segurança e inserem novos bugs na tentativa de consertar os antigos.

## 6. Política de On-Call, Conformidade com a CLT e Saúde Mental Sustentável

Sistemas de saúde operam 24x7x365. Para suportar essa realidade sem destruir a saúde mental dos nossos engenheiros, o regime de plantão (On-Call) da Santo Pegasus é estruturado sob duas premissas absolutas: o rigor matemático na divisão de carga e a conformidade hermética com a Consolidação das Leis do Trabalho (CLT) do Brasil.

Regulamentação e Remuneração (Artigo 244 da CLT): A cultura de "trabalho por amor" em startups frequentemente esconde abusos trabalhistas disfarçados de plantões informais. Na Santo Pegasus, todo engenheiro escalado no PagerDuty fora de seu horário de expediente encontra-se em regime legal de Sobreaviso. Conforme o Art. 244, §2º da CLT, o trabalhador que permanece em sua residência aguardando um eventual chamado (via PagerDuty, Slack ou SMS) é remunerado à razão de 1/3 (um terço) do valor da sua hora normal de trabalho por cada hora de prontidão, mesmo que não haja nenhum incidente no período.

No instante em que o alarme dispara e o engenheiro efetua o "Acknowledge" (reconhecimento do incidente) no sistema, iniciando o trabalho efetivo de mitigação no seu laptop, o regime transaciona instantaneamente. A remuneração passa a ser contada como hora extra integral (100%), acrescida de todos os adicionais previstos em convenções coletivas (Sindpd). Se a atuação ocorrer entre as 22h00 e as 05h00, incide automaticamente o Adicional Noturno (acréscimo de 20%, e a hora computada como 52 minutos e 30 segundos).

Diretrizes de Proteção contra Burnout e Redução de Toil:

- Proibição do "Hero Culture": Escalas de um único indivíduo sendo responsável pelo sistema a semana inteira são proibidas. A rotação mínima on-call deve ter 3 a 4 engenheiros habilitados para garantir ciclos saudáveis de descanso ininterrupto (ex: 1 semana on-call seguida de 3 semanas off-call).
- Descanso Intersticial: Se um engenheiro é acionado às 03h00 da manhã para resolver um SEV-1 que se estende até as 06h00, ele está legalmente e culturalmente isento de comparecer ao Daily Scrum às 09h00. A política dita que ele tenha um período mínimo de 11 horas de repouso consecutivo (Art. 66 da CLT) antes de retomar atividades rotineiras.
- Toil Management (Trabalho Massante): O SRE dita que o acionamento humano fora de hora só é válido se a intervenção exigir intelecto humano genuíno. Se o alerta diz "O disco C: atingiu 95%, limpe os logs", isso é toil. Alertas acionáveis requerem automação (ex: um script AWS Lambda que esvazia a lixeira automaticamente). O objetivo anual de engenharia é que 50% do tempo do time SRE seja gasto em melhorias arquiteturais, e não apagando incêndios previsíveis.

## 7. Fluxo de Resposta a Incidentes: Mitigação vs. Resolução (OODA Loop)

A resposta a incidentes na Santo Pegasus não é um esforço caótico de tentativa e erro, mas um processo militarizado baseado no ciclo OODA (Observe, Orient, Decide, Act). O aspecto mais crucial deste fluxo é a separação hermética entre duas fases: a Mitigação (estabilização imediata para o paciente/médico) e a Resolução (correção definitiva do bug no código).

1. Deteção (Observe): O incidente é capturado pelo monitoramento sintético ou por um alarme de p99 de latência no Prometheus. O pager dispara.
2. Triagem e Declaração (Orient): O engenheiro on-call avalia o impacto (Blast Radius) em menos de 5 minutos. Se confirmar que o módulo de prescrição está fora, ele declara um SEV-1, aciona o Incident Commander e abre a War Room.
3. Mitigação (Decide & Act): Esta é a etapa mais crítica. Na Santo Pegasus, a mitigação prioriza a continuidade do serviço acima de qualquer investigação forense. Se um deploy recente causou instabilidade, a ação imediata não é debugar os logs para entender o porquê da falha; a ação imediata é o rollback. O objetivo da mitigação é devolver uma funcionalidade aceitável ao usuário final o mais rápido possível, interrompendo o sangramento financeiro e o risco clínico.
4. Resolução: Apenas quando o sistema volta ao estado de operação normal e os usuários estão sendo atendidos (ainda que rodando uma versão anterior do sistema), os engenheiros iniciam a investigação profunda, acessando logs de replicação, heap dumps e traces no Datadog.

É importante notar que um incidente pode estar mitigado, mas ainda não resolvido. Exemplo: um ataque DDoS inunda nosso servidor de login. A mitigação é ativar o "Under Attack Mode" no Cloudflare e bloquear IPs de fora da América do Sul. O incidente está mitigado (médicos no Brasil conseguem logar). A resolução será a implementação de regras mais sofisticadas de WAF (Web Application Firewall).

O encerramento (cierre) do incidente envolve a desmobilização formal da War Room, o registro sumário dos eventos pelo Scribe na timeline do Jira, e o agendamento obrigatório de uma sessão de Post-Mortem em até 48 horas.

## 8. Manual Técnico Narrativo de Rollback e Procedimentos de Emergência

A capacidade de reverter alterações de infraestrutura e código em minutos é a maior arma de mitigação da Santo Pegasus. Engessar processos durante uma crise mata o paciente. Abaixo, detalhamos os procedimentos técnicos.

### 8.1 Reversão de Aplicações em Containers (AWS ECS / Fargate)

Quando um novo deploy de um microsserviço Docker causa CrashLoopBackOff ou estouro de memória:

- O engenheiro não deve tentar fazer um "hotfix" commitando código diretamente na branch main contornando a esteira de CI/CD.
- O procedimento de emergência consiste em forçar a AWS a utilizar a última "Task Definition" conhecida como estável.
- Comando de Execução: O Tech Lead executa a CLI da AWS para atualizar o serviço, ignorando temporariamente verificações de saúde estritas (Health Checks) se o Load Balancer estiver bloqueando o tráfego: `aws ecs update-service --cluster agendio-prod --service receita-digital --task-definition receita-digital:142 --force-new-deployment`

### 8.2 Reversão de Infraestrutura (Terraform Drift)

Infraestrutura como Código (IaC) traz previsibilidade, mas um estado corrompido pode destruir ambientes inteiros.

- Antes de qualquer `terraform apply` em produção, a pipeline obrigatoriamente gera um artefato `terraform plan`.
- Se uma alteração acidentalmente remove regras de Security Groups bloqueando o tráfego entre a aplicação e o banco RDS, a mitigação é executar o plano de rollback. A edição manual via console da AWS é estritamente proibida, pois gera Drift (desalinhamento entre o código e a realidade), impossibilitando recuperações futuras.

### 8.3 Banco de Dados: Migrações Compatíveis (Backward Compatibility)

Modificar tabelas em bancos de dados relacionais gigantes (PostgreSQL) bloqueia linhas e gera downtime. Na Santo Pegasus, utilizando Flyway, é mandatória a política de mudanças não destrutivas em 3 fases:

1. Adicionar: Criar a nova coluna sem deletar a antiga. O código escreve nas duas, mas lê da antiga.
2. Migrar e Testar: Os dados são copiados para a nova coluna. O código passa a ler da nova.
3. Limpar (Semanas depois): Apenas após a certeza absoluta de que nenhum código legado depende da estrutura antiga, a coluna velha é deletada. Comandos DDL destrutivos imediatos como `ALTER TABLE agendamentos DROP COLUMN data_antiga;` são bloqueados na esteira, pois um rollback da aplicação falharia por não encontrar mais a coluna que necessita.

## 9. Matemática da Confiabilidade e Gestão de Error Budgets (Orçamentos de Erro)

A confiabilidade não é um sentimento; é uma equação matemática que traduz a saúde técnica em decisões executivas de negócio.

- Service Level Indicator (SLI): É a métrica real e crua extraída dos servidores. Exemplo: "99,95% das requisições de geração de PDF de Receita Médica retornaram HTTP 200 em menos de 500ms".
- Service Level Objective (SLO): É a meta interna rigorosa que a engenharia define. Para sistemas vitais da Santo Pegasus, o SLO é 99,9% (Three Nines) medido em uma janela móvel de 30 dias.
- Service Level Agreement (SLA): É o contrato jurídico externo com as redes hospitalares. Fixado em 99,5%. Se a disponibilidade cair abaixo disso, a Santo Pegasus paga multas pecuniárias contratuais severas. O SLO deve ser sempre mais rígido que o SLA.

Gestão de Error Budgets (O freio da Engenharia): Se o SLO é 99,9%, significa que aceitamos 0,1% de falha. Em um mês (730 horas), isso equivale a um "Orçamento de Erro" de exatamente 43 minutos e 12 segundos de indisponibilidade permitida.

> **Nota de transcrição:** o documento de origem informa 43 minutos e 12
> segundos, mas 0,1% de 730 horas equivale a 43 minutos e 48 segundos. O
> valor foi transcrito como está no original.

Este orçamento é a moeda de troca para inovar. Cada deploy traz risco e "gasta" um pouco deste orçamento. Se uma sequência de bad deploys esgota esses 43 minutos (um burn rate excessivo), aciona-se a política mais radical da cultura SRE: o Feature Freeze (Congelamento de Lançamentos). Durante um Feature Freeze, a diretoria de produto não pode exigir o lançamento de novas funcionalidades de negócio. 100% da capacidade produtiva dos desenvolvedores é redirecionada por decreto para o pagamento de débitos técnicos, automação de testes de estresse e otimização de queries de banco de dados, até que o Orçamento de Erro seja recuperado na janela de 30 dias. A confiabilidade sempre vence a velocidade de mercado.

## 10. Cultura 5 Porquês, O Post-Mortem e a Notificação à ANPD

Após a estabilização de um incidente SEV-1 ou SEV-2, o evento deve ser transformado em capital intelectual corporativo. O Post-Mortem é um documento público internamente, isento de apontamento de culpados, focado em entender a cadeia de falhas.

Utilizamos a técnica dos "5 Porquês" (Root Cause Analysis) para perfurar a superfície do erro:

1. Por que o sistema caiu? Porque o banco de dados principal parou de aceitar conexões.
2. Por que parou de aceitar conexões? Porque o limite máximo de Max Connections foi atingido.
3. Por que o limite foi atingido? Porque o microsserviço de faturamento iniciou um loop infinito criando novas conexões sem reciclar as antigas.
4. Por que houve um loop infinito? Porque um timeout na API da adquirente de cartão de crédito não estava sendo tratado adequadamente.
5. Por que o erro de timeout chegou à produção? Porque não temos testes de caos (Chaos Testing) que simulem lentidão extrema de parceiros terceirizados na nossa esteira de CI/CD.

A ação reparatória (Action Item) não é "dar bronca no desenvolvedor", mas sim implementar um Circuit Breaker no código e incluir injeção de falhas nos testes automatizados.

Conformidade Jurídica e a LGPD (Lei Geral de Proteção de Dados): No setor de saúde (Artigo 11 da LGPD), vazamentos de dados são infrações gravíssimas. Se durante a investigação o Post-Mortem revelar que dados de prontuários, CPFs ou receitas de pacientes foram expostos indevidamente na internet (ex: um bucket S3 configurado como público), o Data Protection Officer (DPO) da Santo Pegasus assume o controle. A lei exige notificação formal e fundamentada à Autoridade Nacional de Proteção de Dados (ANPD) em um prazo estritamente de 2 (dois) dias úteis após a ciência inequívoca do evento. Ocultar o vazamento para tentar consertar silenciosamente agrava a multa (que pode chegar a R$ 50 milhões). O Post-Mortem rigoroso serve como documento jurídico comprobatório de que a empresa agiu com diligência técnica, cooperando para reduzir possíveis ações por danos morais.

## 11. Chaos Engineering: Validando a Resiliência sob Pressão

Protocolos em papel não sobrevivem ao contato com a realidade se não forem testados. Para garantir que este manual funcione e que os sistemas de auto-recuperação operem como esperado, a Santo Pegasus pratica Engenharia do Caos (Chaos Engineering) através de eventos regulares chamados GameDays.

Através de simulações controladas (inicialmente em ambiente de Staging, evoluindo para Produção sob monitoramento extremo), a equipe de SRE introduz falhas técnicas deliberadas no ecossistema sem avisar previamente os desenvolvedores de plantão:

- Caída de Zonas (AZ Failure): Forçar o desligamento de todas as instâncias em uma Zona de Disponibilidade da AWS (ex: `sa-east-1a`) para verificar se o tráfego é automaticamente roteado para a zona `sa-east-1b` sem interrupção perceptível para o médico.
- Injeção de Latência (Network Blackhole): Adicionar artificialmente 5.000 milissegundos de latência nas chamadas para a API de elegibilidade da ANS para testar se os nossos Circuit Breakers abrem corretamente e ativam o fluxo de contingência (fallback) em vez de travar toda a aplicação.
- Exaustão de Recursos: Consumir propositalmente 100% da CPU de nós do Kubernetes para validar se o Horizontal Pod Autoscaler (HPA) cria novas réplicas de forma ágil o suficiente.

O GameDay tem dois objetivos: testar a robustez arquitetônica do software sob estresse destrutivo e, mais criticamente, treinar a resposta humana. Ele testa se o Incident Commander consegue gerenciar o pânico, se o Tech Lead consegue encontrar os logs certos em meio ao caos e se o alerta no PagerDuty toca para a pessoa correta na hora correta. Ter "músculos treinados" na paz é o que garante a precisão cirúrgica e a proteção da vida do paciente durante a guerra de um incidente real.

Propriedade da Santo Pegasus Soluciones. Versão 6.0 — Julho de 2026. Todos os direitos reservados.
