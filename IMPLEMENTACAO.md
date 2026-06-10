# Implementação da Integração com Banco de Dados — Consulta CPF

> **Documento:** Guia oficial de implementação da demanda recebida da área de negócio  
> **Status:** Pré-implementação — análise e planejamento  
> **Projeto:** ConsultaCPF — JUCERJA  
> **Versão analisada:** 0.0.1-SNAPSHOT  
> **Data:** 10/06/2026  
> **Destinado a:** desenvolvedor(a) responsável pela implementação

---

## Sumário

1. [Contexto da Demanda](#1-contexto-da-demanda)
2. [Entendimento da Solução](#2-entendimento-da-solução)
3. [Fluxo Atual da Aplicação](#3-fluxo-atual-da-aplicação)
4. [Fluxo Futuro Após a Implementação](#4-fluxo-futuro-após-a-implementação)
5. [Estratégia de Implementação](#5-estratégia-de-implementação)
6. [Fase 1 — Análise do Banco](#6-fase-1--análise-do-banco)
7. [Fase 2 — Configuração de Banco de Dados](#7-fase-2--configuração-de-banco-de-dados)
8. [Fase 3 — Mapeamento das Entidades](#8-fase-3--mapeamento-das-entidades)
9. [Fase 4 — Camada de Persistência](#9-fase-4--camada-de-persistência)
10. [Fase 5 — Integração com o Fluxo SOAP](#10-fase-5--integração-com-o-fluxo-soap)
11. [Fase 6 — Atualização dos Dados](#11-fase-6--atualização-dos-dados)
12. [Fase 7 — Testes](#12-fase-7--testes)
13. [Possíveis Dificuldades](#13-possíveis-dificuldades)
14. [Decisões Arquiteturais Recomendadas](#14-decisões-arquiteturais-recomendadas)
15. [Checklist de Implementação](#15-checklist-de-implementação)

---

## 1. Contexto da Demanda

### Como o sistema funciona atualmente

A aplicação `ConsultaCPF` é um sistema desenvolvido para a **JUCERJA** (Junta Comercial do Estado do Rio de Janeiro) que realiza consultas de CPF junto à **Receita Federal do Brasil (RFB)** através de um serviço **SOAP** intermediado pelo **SERPRO** (Serviço Federal de Processamento de Dados).

Hoje, o sistema funciona de forma completamente **stateless** — sem estado, sem banco de dados, sem memória de execuções anteriores. Cada consulta começa do zero e termina sem deixar rastro.

O funcionamento atual pode ser descrito em cinco palavras: **usuário digita, sistema consulta, resultado aparece.**

### Como funciona hoje a consulta SOAP

SOAP (Simple Object Access Protocol) é um protocolo de comunicação que usa XML para troca de mensagens entre sistemas. Ao contrário de uma API REST moderna que trafega JSON, o SOAP trafega XML com uma estrutura rígida chamada **envelope**.

O serviço externo utilizado é o **WS09** da RFB, acessado no ambiente de homologação através da URL:
```
http://rfb.hml.jucerja.rj.gov.br/services/ws09/ws09
```

O sistema monta uma mensagem XML neste formato, envia via HTTP POST e recebe outra mensagem XML de resposta. Toda essa construção e interpretação do XML é feita manualmente pelo código da aplicação, usando uma tecnologia chamada **JAXB** (Jakarta XML Binding).

### O que a aplicação faz atualmente

1. O usuário acessa `http://localhost:8081` no navegador
2. Uma tela simples exibe um campo de texto e um botão "Consultar"
3. O usuário digita um número de CPF e clica no botão
4. O frontend JavaScript faz uma chamada para `GET /cpf/{numeroCPF}`
5. O backend constrói uma mensagem SOAP com os dados do CPF
6. A mensagem é enviada ao serviço externo da RFB via HTTP
7. A resposta XML é recebida e interpretada
8. O código de situação cadastral é traduzido para texto legível (ex.: `"0"` → `"Regular"`)
9. O texto é exibido na tela do usuário
10. **Nenhum dado é salvo em banco. Nenhum histórico é gerado.**

### O que foi solicitado

Foi criado um banco SQL Server chamado **`TesteConsultaCPF`** com três tabelas:

- **`DadosCPF`** — tabela principal com os CPFs que precisam ser consultados
- **`ConsultaCPFS09`** — tabela de histórico onde o XML de cada resposta SOAP deve ser salvo
- **`SituacaoCadastralCPF`** — tabela de domínio com os códigos de situação cadastral

As orientações recebidas foram:

> 1. A tabela principal é `DadosCPF`
> 2. O sistema deverá **ler o CPF** existente na tabela `DadosCPF`
> 3. Após a consulta ao serviço SOAP S09, o **XML retornado deverá ser salvo** na tabela `ConsultaCPFS09`
> 4. Após salvar o XML, a tabela `DadosCPF` deverá ser **atualizada** com `SITUACAOCADASTRALRFBID`, `CONSULTAS09ID` e `DATAATUALIZACAO`
> 5. Os **códigos de situação cadastral** utilizados pela RFB estão armazenados na tabela `SituacaoCadastralCPF`
> 6. Os **relacionamentos entre as tabelas** deverão ser respeitados

### Qual o objetivo de negócio da nova demanda

Hoje o sistema é uma ferramenta de consulta pontual e descartável. A nova demanda transforma o sistema em um **processo de atualização cadastral rastreável e auditável**.

O que a área de negócio quer, em linguagem simples, é:

- **Rastreabilidade:** saber exatamente o que a RFB respondeu para cada CPF consultado, preservando o XML original
- **Auditoria:** registrar quando cada CPF foi consultado e qual situação foi encontrada
- **Integração de dados:** cruzar os dados de CPF com a situação cadastral dentro do próprio banco da JUCERJA
- **Automação:** o sistema deixa de depender de um usuário digitando CPFs manualmente — ele lê a lista de CPFs do banco e os processa

Em vez de ser uma ferramenta avulsa de consulta, o sistema passa a ser uma **peça de integração de dados** dentro do ecossistema da JUCERJA.

---

## 2. Entendimento da Solução

### O que representa cada tabela

#### Tabela `DadosCPF` — a tabela principal

Esta é a tabela central do novo fluxo. Ela já contém os CPFs que precisam ser consultados na Receita Federal. Pense nela como uma **fila de trabalho**: cada linha é um CPF aguardando (ou já tendo recebido) uma consulta.

Após a implementação, cada registro nesta tabela terá:
- O número do CPF (já existente antes da implementação)
- Uma referência para qual consulta S09 gerou o resultado (`CONSULTAS09ID`)
- Uma referência para qual situação cadastral foi encontrada (`SITUACAOCADASTRALRFBID`)
- A data e hora em que a atualização ocorreu (`DATAATUALIZACAO`)

Um CPF cujos campos `CONSULTAS09ID` e `SITUACAOCADASTRALRFBID` estejam nulos pode ser interpretado como **pendente de consulta** — ainda não foi processado pelo sistema.

#### Tabela `ConsultaCPFS09` — o histórico auditável

Esta tabela é um **arquivo de evidências**. Cada vez que o sistema consulta um CPF no serviço S09, a resposta completa em XML deve ser salva aqui.

Por que salvar o XML bruto e não só o resultado? Porque o XML contém todos os dados retornados pela RFB — nome, data de nascimento, situação — e sua preservação permite:
- Reconstruir o que a RFB informou em qualquer data no passado
- Auditar se o sistema interpretou corretamente o resultado
- Rastrear o histórico de situações de um CPF ao longo do tempo
- Detectar discrepâncias caso a RFB retorne dados inesperados

Cada registro desta tabela representa **uma chamada ao serviço S09**, com seu resultado preservado integralmente.

#### Tabela `SituacaoCadastralCPF` — a tabela de domínio

Esta é uma **tabela de referência** (também chamada de tabela de domínio ou lookup table). Ela armazena os códigos que a Receita Federal utiliza para classificar a situação de um CPF.

Atualmente, esses códigos estão hardcoded no código Java dentro do `CpfService.java` (método `traduzirSituacao`). Após a implementação, esses mesmos códigos existirão no banco, e o sistema os usará para estabelecer um relacionamento formal entre o resultado da consulta e o registro do CPF.

Os códigos conhecidos são:

| Código RFB | Descrição |
|---|---|
| 0 | Regular |
| 1 | Cancelada por encerramento de espólio |
| 2 | Suspensa |
| 3 | Cancelada óbito sem espólio |
| 4 | Pendente de Regularização |
| 5 | Cancelada multiplicidade |
| 8 | Nula |
| 9 | Cancelada de ofício |

### Como as tabelas se relacionam

```
┌──────────────────────────────────────────────────────────────────────┐
│                    SituacaoCadastralCPF                              │
│                                                                      │
│  PK: ID                                                              │
│      CODIGO        (ex.: "0", "2", "9")                              │
│      DESCRICAO     (ex.: "Regular", "Suspensa", ...)                 │
└────────────────────────┬─────────────────────────────────────────────┘
                         │ referenciada por
                         │ SITUACAOCADASTRALRFBID (FK)
                         │
┌────────────────────────▼─────────────────────────────────────────────┐
│                         DadosCPF                          (CENTRAL)  │
│                                                                      │
│  PK: ID                                                              │
│      CPF                        (número do CPF)                      │
│      SITUACAOCADASTRALRFBID  FK → SituacaoCadastralCPF.ID            │
│      CONSULTAS09ID           FK → ConsultaCPFS09.ID                  │
│      DATAATUALIZACAO            (quando foi atualizado)              │
└────────────────────────┬─────────────────────────────────────────────┘
                         │ referenciada por
                         │ CONSULTAS09ID (FK)
                         │
┌────────────────────────▼─────────────────────────────────────────────┐
│                       ConsultaCPFS09                                 │
│                                                                      │
│  PK: ID                                                              │
│      XML_RESPOSTA   (XML bruto retornado pelo S09)                   │
│      DATA_CONSULTA  (quando a consulta foi realizada)                │
│      ... (outros campos a confirmar com o banco real)                │
└──────────────────────────────────────────────────────────────────────┘
```

### Qual o papel de cada tabela no fluxo

```
ANTES DA IMPLEMENTAÇÃO:
  DadosCPF  →  CPF existe no banco, mas os campos de situação estão nulos
  ConsultaCPFS09  →  vazia (nenhuma consulta foi salva ainda)
  SituacaoCadastralCPF  →  já populada com os códigos RFB

DEPOIS DA IMPLEMENTAÇÃO (após processar um CPF):
  DadosCPF  →  CPF atualizado com situação, referência à consulta e data
  ConsultaCPFS09  →  contém o XML da resposta do S09 para aquele CPF
  SituacaoCadastralCPF  →  não alterada (é apenas lida)
```

---

## 3. Fluxo Atual da Aplicação

O fluxo atual pode ser lido em dois níveis: o que o usuário vê, e o que acontece por baixo dos panos.

### Do ponto de vista do usuário

1. Abre o navegador em `http://localhost:8081`
2. Vê uma tela simples com um campo de texto e um botão
3. Digita o CPF desejado
4. Clica em "Consultar"
5. Aguarda alguns segundos
6. Vê o texto da situação cadastral aparecer na tela (ex.: "Regular")

### Do ponto de vista técnico

```
┌─────────────┐
│   USUÁRIO   │
│  (Browser)  │
└──────┬──────┘
       │ 1. Digita CPF e clica "Consultar"
       │    JavaScript executa: fetch('/cpf/12345678900')
       ▼
┌─────────────────────────────────────────┐
│  index.html + script.js                 │
│  (Frontend estático em /static/)        │
│                                         │
│  async function consultarCPF() {        │
│    fetch(`/cpf/${cpf}`)                 │  → emite GET /cpf/12345678900
│    resultado.innerHTML = texto          │
│  }                                      │
└──────┬──────────────────────────────────┘
       │ 2. GET /cpf/12345678900
       ▼
┌─────────────────────────────────────────┐
│  CpfController.java                     │
│  @RestController @RequestMapping("/cpf")│
│                                         │
│  @GetMapping("/{cpf}")                  │
│  public String verificar(               │
│      @PathVariable String cpf)          │  → chama service.verificarSituacao(cpf)
└──────┬──────────────────────────────────┘
       │ 3. Delega ao Service
       ▼
┌─────────────────────────────────────────┐
│  CpfService.java                        │
│  @Service                               │
│                                         │
│  public String verificarSituacao(cpf) { │
│    ConsultaCPFResponse response =       │
│        soapClient.consultarCpf(cpf)     │  → chama o client SOAP
│                                         │
│    // validações de nulidade            │
│    // extrai situacaoCadastral          │
│    // traduz código → descrição         │
│    return descricaoSituacao             │
│  }                                      │
└──────┬──────────────────────────────────┘
       │ 4. Delega ao Client SOAP
       ▼
┌─────────────────────────────────────────┐
│  CpfSoapClient.java                     │
│  @Component                             │
│                                         │
│  1. Monta ConsultaCPFRequest            │
│  2. Envolve em SoapEnvelope             │
│  3. XmlParser.toXml() → String XML      │  → serializa objeto em XML
│  4. Configura RestTemplate              │
│  5. Define headers SOAP 1.2             │
│  6. HTTP POST → serviço externo         │  → envia para a RFB/SERPRO
│  7. Recebe ResponseEntity<String>       │
│  8. XmlParser.fromXml() → objeto        │  → desserializa XML em objeto
│  9. Retorna ConsultaCPFResponse         │
└──────┬──────────────────────────────────┘
       │ 5. HTTP POST com XML SOAP
       ▼
┌─────────────────────────────────────────┐
│  SERVIÇO EXTERNO                        │
│  SERPRO / RFB — WS09                    │
│  (Homologação)                          │
│                                         │
│  Recebe envelope SOAP, processa CPF,    │
│  retorna envelope SOAP com dados        │
└──────┬──────────────────────────────────┘
       │ 6. Resposta XML SOAP
       ▼
 [volta pelo mesmo caminho]
       │
       │ 7. ConsultaCPFResponse retorna ao CpfService
       │    CpfService extrai situacaoCadastral e traduz
       │    Retorna String "Regular" (ou outra descrição)
       │
       ▼
┌─────────────────────────────────────────┐
│  HTTP Response 200 OK                   │
│  Body: "Regular"                        │
└──────┬──────────────────────────────────┘
       │ 8. JavaScript recebe texto
       │    resultado.innerHTML = "Regular"
       ▼
┌─────────────────────────────────────────┐
│  USUÁRIO VÊ: "Regular" na tela          │
└─────────────────────────────────────────┘
```

### Problemas do fluxo atual em relação à nova demanda

| Problema | Impacto |
|---|---|
| O CPF vem do usuário, não do banco | Não é possível processar uma lista de CPFs automaticamente |
| O XML da resposta é descartado após a desserialização | Não há histórico das respostas da RFB |
| Nenhum dado é salvo | Não há rastreabilidade, auditoria nem atualização cadastral |
| Os códigos de situação estão hardcoded no Java | Não há relação formal com a tabela `SituacaoCadastralCPF` do banco |

---

## 4. Fluxo Futuro Após a Implementação

### Visão geral do novo fluxo

O ponto de partida muda completamente: em vez do usuário digitar um CPF, o sistema será **disparado por uma chamada** (manual ou programática) e irá **ler os CPFs do banco**.

```
┌──────────────────────────────────────────────────────────────────────┐
│  GATILHO (ex.: endpoint REST POST /cpf/processar)                    │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Controller                                                          │
│  Recebe a requisição de processamento e delega ao Service            │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Service de Processamento (novo)                                     │
│                                                                      │
│  1. Consulta DadosCpfRepository → busca CPFs pendentes no banco      │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ lista de CPFs pendentes
                               ▼
                    ┌──────────────────────┐
                    │  Para cada CPF       │ ◄──────────────────────┐
                    │  da lista:           │                         │
                    └──────────┬───────────┘                         │
                               │                                     │
                               ▼                                     │
┌──────────────────────────────────────────────────────────────────────┐
│  CpfSoapClient (adaptado)                                            │
│  Chama o serviço S09 → retorna XML bruto + objeto desserializado     │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ XML bruto + ConsultaCPFResponse
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  ConsultaCpfS09Repository                                            │
│  Salva o XML bruto na tabela ConsultaCPFS09                          │
│  Retorna o ID do registro criado                                     │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ ID da consulta salva
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  SituacaoCadastralCpfRepository                                      │
│  Busca na tabela SituacaoCadastralCPF o registro                     │
│  correspondente ao código retornado pela RFB (ex.: "0")              │
│  Retorna o ID desse registro                                         │
└──────────────────────────────┬───────────────────────────────────────┘
                               │ ID da situação cadastral
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  DadosCpfRepository                                                  │
│  Atualiza o registro na tabela DadosCPF com:                         │
│    • SITUACAOCADASTRALRFBID = ID encontrado na tabela de situação    │
│    • CONSULTAS09ID          = ID do XML salvo                        │
│    • DATAATUALIZACAO        = data/hora atual                        │
└──────────────────────────────┬───────────────────────────────────────┘
                               │
                               └─────────────────────────────────────► próximo CPF
                                                                       (retorna ao loop)

Quando todos os CPFs forem processados:
                               │
                               ▼
┌──────────────────────────────────────────────────────────────────────┐
│  Resposta ao chamador                                                │
│  (ex.: "X CPFs processados com sucesso, Y com erro")                │
└──────────────────────────────────────────────────────────────────────┘
```

### Detalhamento de cada etapa do novo fluxo

#### Etapa 1 — Gatilho

O processamento precisa ser iniciado de alguma forma. As opções mais comuns são:

- **Endpoint REST manual** (ex.: `POST /cpf/processar`): alguém chama a API quando quiser processar a fila
- **Agendamento automático** (`@Scheduled`): a aplicação roda o processamento em horário definido (ex.: todo dia às 3h da manhã)

A decisão de qual usar deve ser feita junto com a área de negócio. O código da lógica de processamento será o mesmo nos dois casos — apenas o gatilho muda.

#### Etapa 2 — Leitura dos CPFs pendentes

O novo Service consulta a tabela `DadosCPF` buscando registros que ainda não foram processados. O critério de "pendente" precisa ser confirmado com a área de negócio, mas o mais provável é: registros onde `CONSULTAS09ID` é nulo.

#### Etapa 3 — Chamada ao serviço SOAP

Esta etapa é similar ao fluxo atual, mas com uma diferença importante: o `CpfSoapClient` precisará retornar **tanto o XML bruto quanto o objeto desserializado**. Hoje ele descarta o XML após interpretar. Com a nova demanda, o XML precisa ser preservado para salvamento.

#### Etapa 4 — Persistência do XML

O XML bruto recebido do S09 é salvo na tabela `ConsultaCPFS09`. Após o `save()`, o banco gera um ID para esse registro, que será usado na etapa seguinte.

#### Etapa 5 — Localização da situação cadastral

O código retornado pela RFB (ex.: `"0"`) precisa ser cruzado com a tabela `SituacaoCadastralCPF` para obter o ID correspondente. Este ID é um valor de chave estrangeira — não se salva o código texto, mas sim a referência ao registro da tabela de domínio.

#### Etapa 6 — Atualização do registro de CPF

Com os dois IDs em mãos (da consulta e da situação), o registro na tabela `DadosCPF` é atualizado. A `DATAATUALIZACAO` recebe a data e hora atual do servidor.

#### Etapa 7 — Retorno ao chamador

Após processar todos os CPFs da fila, o sistema retorna alguma confirmação. Exatamente o que retornar (um contador, uma lista de CPFs processados, erros encontrados) é uma decisão de implementação que pode ser discutida com a área de negócio.

---

## 5. Estratégia de Implementação

A implementação está dividida em **7 fases sequenciais**. Cada fase tem pré-requisitos das anteriores. Não pule fases — cada uma prepara a base para a próxima.

```
Fase 1 — Análise do Banco          (nenhum arquivo — só entendimento)
    ↓
Fase 2 — Configuração de Banco     (pom.xml + application.yaml)
    ↓
Fase 3 — Mapeamento das Entidades  (3 novas classes @Entity)
    ↓
Fase 4 — Camada de Persistência    (3 novas interfaces Repository)
    ↓
Fase 5 — Integração com SOAP       (adaptar CpfSoapClient)
    ↓
Fase 6 — Atualização dos Dados     (novo Service de processamento)
    ↓
Fase 7 — Testes                    (unidade + integração)
```

As fases 3, 4 e 5 têm dependência apenas da Fase 2 e podem ser desenvolvidas em paralelo por pessoas diferentes se necessário. A Fase 6 depende de todas as anteriores.

---

## 6. Fase 1 — Análise do Banco

### Objetivo

Mapear com precisão e antecedência a estrutura real das três tabelas do banco `TesteConsultaCPF` antes de qualquer código ser escrito.

### Motivação

Mapeamentos de entidade JPA feitos com base em suposições geram erros difíceis de rastrear. Uma coluna com nome diferente do esperado, um tipo incompatível ou um relacionamento com cardinalidade errada podem fazer a aplicação falhar silenciosamente ou produzir dados inconsistentes. Investir tempo nesta fase economiza horas de debugging nas fases seguintes.

### O que precisa ser validado

**Na tabela `DadosCPF`:**
- Listar todas as colunas, seus tipos SQL exatos e se aceitam nulo (`nullable`)
- Identificar a chave primária: é um `INT` com auto-incremento? Um `BIGINT`? Um `UNIQUEIDENTIFIER` (UUID)?
- Confirmar os nomes exatos de `SITUACAOCADASTRALRFBID`, `CONSULTAS09ID` e `DATAATUALIZACAO`
- Identificar o tipo da coluna CPF: `VARCHAR(11)`? `CHAR(11)`? `VARCHAR(14)` (com máscara)?
- Verificar se existe alguma coluna adicional de status ou flag que indique "processado/pendente"
- Confirmar se há constraints de unicidade no CPF

**Na tabela `ConsultaCPFS09`:**
- Listar todas as colunas e tipos
- Identificar qual coluna armazena o XML: `VARCHAR(MAX)`? `NVARCHAR(MAX)`? `XML`? `TEXT`?
- Verificar se existe coluna de data/hora da consulta e quem a preenche (aplicação ou `DEFAULT GETDATE()` no banco)
- Identificar o nome exato da chave primária
- Verificar se existe FK de volta para `DadosCPF` (relação bidirecional?)

**Na tabela `SituacaoCadastralCPF`:**
- Listar todas as colunas e tipos
- Identificar qual coluna contém o código da RFB (o número "0" a "9")
- Confirmar se a tabela já está populada com os códigos conhecidos
- Verificar se os códigos `6` e `7` estão presentes (atualmente não mapeados no código Java)

### O que observar nos relacionamentos

No SQL Server, os relacionamentos são definidos como Foreign Keys. Execute o seguinte para inspecionar as chaves estrangeiras:

```sql
-- Listar todas as FK do banco
SELECT 
    fk.name AS FK_name,
    tp.name AS tabela_pai,
    cp.name AS coluna_pai,
    tr.name AS tabela_referenciada,
    cr.name AS coluna_referenciada
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fk.object_id = fkc.constraint_object_id
JOIN sys.tables tp ON fkc.parent_object_id = tp.object_id
JOIN sys.columns cp ON fkc.parent_object_id = cp.object_id AND fkc.parent_column_id = cp.column_id
JOIN sys.tables tr ON fkc.referenced_object_id = tr.object_id
JOIN sys.columns cr ON fkc.referenced_object_id = cr.object_id AND fkc.referenced_column_id = cr.column_id
ORDER BY tp.name;
```

Para ver o schema de uma tabela específica:
```sql
-- Estrutura completa de uma tabela
EXEC sp_help 'DadosCPF';
EXEC sp_help 'ConsultaCPFS09';
EXEC sp_help 'SituacaoCadastralCPF';
```

### O que deve estar documentado ao final desta fase

- Schema completo das 3 tabelas (colunas, tipos, nullable, defaults)
- Diagrama de relacionamentos com cardinalidade (1:N, N:1)
- Tipo da chave primária de cada tabela
- Critério de "CPF pendente" confirmado
- Conteúdo atual da tabela `SituacaoCadastralCPF`

---

## 7. Fase 2 — Configuração de Banco de Dados

### Objetivo

Fazer a aplicação Spring Boot se conectar ao SQL Server `TesteConsultaCPF` com sucesso, sem nenhuma outra funcionalidade ainda implementada.

### Motivação

Isolar a configuração de banco em uma fase própria permite validar a conectividade antes de adicionar complexidade. Se houver problema de rede, credenciais ou driver, é muito mais fácil diagnosticar quando não há lógica de negócio envolvida.

### O que será criado/alterado

**No `pom.xml` — novas dependências:**

Serão necessárias duas dependências principais:

1. **`spring-boot-starter-data-jpa`**: habilita o Spring Data JPA na aplicação. Isso traz o Hibernate (implementação JPA), gerenciamento de transações, repositórios automáticos e integração com o ciclo de vida do Spring.

2. **Driver JDBC do SQL Server** (`com.microsoft.sqlserver:mssql-jdbc`): é o "tradutor" que permite ao Java falar com o SQL Server. Sem ele, qualquer tentativa de conexão falhará.

**No `application.yaml` — configuração de datasource:**

Será necessário um bloco de configuração com:

- **URL de conexão JDBC**: formato `jdbc:sqlserver://HOST:PORTA;databaseName=TesteConsultaCPF`
- **Usuário e senha** do banco
- **Nome da classe do driver**: `com.microsoft.sqlserver.jdbc.SQLServerDriver`
- **Configurações JPA**:
  - `hibernate.ddl-auto: validate` — o Hibernate apenas verifica se o schema bate com as entidades, sem alterar nada no banco
  - `show-sql: true` — temporariamente, para facilitar o debug durante o desenvolvimento
  - Dialeto SQL Server, se necessário

> **Atenção:** nunca use `ddl-auto: create`, `create-drop` ou `update` em um banco que já tem dados. Essas opções podem apagar tabelas ou alterar colunas existentes.

### Como isso se encaixa na arquitetura atual

Hoje o `application.yaml` tem apenas 4 linhas. Após esta fase, ele terá um bloco substancialmente maior. A aplicação continuará funcionando da mesma forma para as requisições existentes — a adição do banco não quebra o que já existe.

O `@SpringBootApplication` já habilita o auto-configure do Spring Boot, que detecta automaticamente o `spring-boot-starter-data-jpa` no classpath e configura o Hibernate sem que seja necessário criar uma classe `@Configuration` manual.

### Validação desta fase

A fase 2 está concluída quando:
- A aplicação sobe sem erros de conexão no console
- O log exibe a mensagem de conexão bem-sucedida do Hibernate
- O endpoint existente (`GET /cpf/{cpf}`) continua funcionando normalmente

---

## 8. Fase 3 — Mapeamento das Entidades

### Objetivo

Criar as classes Java que representam as tabelas do banco. Cada entidade é um espelho em código do schema definido no banco de dados.

### Motivação

As entidades são o vocabulário que o JPA usa para traduzir entre o mundo Java (objetos) e o mundo SQL (linhas e colunas). Sem entidades corretamente mapeadas, não é possível fazer nenhuma operação de leitura ou escrita.

### O que é uma entidade JPA

Uma entidade é uma classe Java anotada com `@Entity` que representa uma tabela do banco. Cada atributo da classe corresponde a uma coluna da tabela. Anotações como `@Column`, `@Id`, `@ManyToOne` e `@JoinColumn` ensinam ao JPA como fazer essa correspondência.

### Entidades que deverão existir

#### `SituacaoCadastralCpfEntity`

**Responsabilidade:** representar a tabela `SituacaoCadastralCPF`.

**Características:**
- É a entidade mais simples das três
- É **somente leitura** dentro do fluxo de processamento — o sistema a lê, mas nunca a altera
- Contém o ID (chave primária), o código da RFB (ex.: `"0"`) e a descrição (ex.: `"Regular"`)
- Não possui relacionamentos com outras entidades

**Por que começar por ela:** as outras entidades dependem dela (via chave estrangeira), então faz sentido mapeá-la primeiro.

#### `ConsultaCpfS09Entity`

**Responsabilidade:** representar a tabela `ConsultaCPFS09`.

**Características:**
- Armazena o XML bruto retornado pelo serviço S09
- Possivelmente contém a data/hora da consulta
- É **criada** pelo fluxo de processamento (cada execução bem-sucedida cria um registro novo)
- Seu ID gerado pelo banco será usado para atualizar `DadosCPF`

**Atenção especial:** a coluna que armazena o XML pode ser muito grande (`VARCHAR(MAX)` pode conter até 2 GB de texto). O mapeamento deve usar o tipo correto em Java (`String`) e pode requerer a anotação `@Lob` dependendo do tipo SQL escolhido.

#### `DadosCpfEntity`

**Responsabilidade:** representar a tabela `DadosCPF` — a tabela principal do fluxo.

**Características:**
- É a entidade mais complexa — possui dois relacionamentos de chave estrangeira
- É **lida** no início do processamento (busca CPFs pendentes) e **atualizada** no final (após salvar o XML e localizar a situação)
- Os campos `SITUACAOCADASTRALRFBID` e `CONSULTAS09ID` são chaves estrangeiras que referenciam as outras duas entidades
- O campo `DATAATUALIZACAO` será preenchido com a data/hora atual no momento da atualização

**Relacionamentos:**
- `SITUACAOCADASTRALRFBID` → `@ManyToOne` para `SituacaoCadastralCpfEntity`
- `CONSULTAS09ID` → `@ManyToOne` para `ConsultaCpfS09Entity`

### Como elas se relacionam em código (estrutura esperada)

```
DadosCpfEntity
    │
    ├── @ManyToOne
    │   @JoinColumn(name = "SITUACAOCADASTRALRFBID")
    │   SituacaoCadastralCpfEntity situacaoCadastral
    │
    └── @ManyToOne
        @JoinColumn(name = "CONSULTAS09ID")
        ConsultaCpfS09Entity consultaS09
```

### Onde criar as entidades

Recomenda-se criar um pacote dedicado dentro da estrutura existente:

```
src/main/java/com/jucerja/consultaCpf/
    └── entity/
        ├── DadosCpfEntity.java
        ├── ConsultaCpfS09Entity.java
        └── SituacaoCadastralCpfEntity.java
```

### Cuidados importantes nesta fase

- **Use os nomes exatos das colunas** definidos no banco via `@Column(name = "NOME_EXATO")`. O SQL Server é case-insensitive para nomes, mas é boa prática manter exatidão.
- **Use `@Table(name = "NomeDaTabela")` em cada entidade** para vincular explicitamente ao nome real da tabela no banco.
- **Configure o `FetchType` dos relacionamentos** conscientemente. `EAGER` carrega objetos relacionados automaticamente (pode gerar queries desnecessárias). `LAZY` carrega sob demanda (mais eficiente, mas requer atenção com transações). Para este caso, `LAZY` é geralmente a escolha correta.
- **Não derive a estrutura das entidades de suposições** — confirme cada campo na Fase 1.

---

## 9. Fase 4 — Camada de Persistência

### Objetivo

Criar as interfaces `Repository` que permitem ao Service interagir com o banco de dados de forma limpa, sem escrever SQL manual para operações comuns.

### Motivação

O Spring Data JPA tem uma das características mais poderosas do ecossistema Spring: ao criar uma interface que estende `JpaRepository`, o Spring **gera automaticamente** a implementação em tempo de execução. Operações de busca, save, delete e update básicos já estão disponíveis sem uma única linha de SQL.

### O que é um Repository

Um Repository é uma interface Java (não uma classe — apenas a assinatura dos métodos). O Spring Data lê essa interface, entende os métodos declarados e gera o SQL correspondente automaticamente.

Por exemplo, um método chamado `findByConsultaS09IsNull()` em um repository de `DadosCpfEntity` seria interpretado pelo Spring como:
```sql
SELECT * FROM DadosCPF WHERE CONSULTAS09ID IS NULL
```

Nenhum SQL precisa ser escrito. O framework faz o trabalho.

### Repositories que deverão existir

#### `SituacaoCadastralCpfRepository`

**Responsabilidade:** buscar registros na tabela `SituacaoCadastralCPF`.

**Como será usado:** o Service buscará o ID correspondente a um código específico retornado pela RFB. Exemplo: "preciso do registro cuja coluna CODIGO seja igual a '0'". Esse método precisa ser declarado na interface (ex.: `findByCodigo(String codigo)`).

**Operações necessárias:**
- Busca por código da RFB → retorna a entidade (com o ID)
- Não precisará de `save()` — essa tabela não é modificada pelo sistema

#### `ConsultaCpfS09Repository`

**Responsabilidade:** salvar registros na tabela `ConsultaCPFS09`.

**Como será usado:** o Service criará um novo objeto `ConsultaCpfS09Entity`, preencherá com o XML bruto e chamará `save()`. O banco gerará o ID automaticamente (via auto-incremento ou sequence), e o JPA retornará o objeto atualizado com o ID preenchido.

**Operações necessárias:**
- `save(ConsultaCpfS09Entity entity)` → já herdado de `JpaRepository`
- Nenhum método customizado esperado

#### `DadosCpfRepository`

**Responsabilidade:** ler CPFs pendentes e atualizar registros após o processamento.

**Como será usado:**
1. No início do processamento: buscar todos os CPFs pendentes (método customizado)
2. Após o processamento: atualizar o registro com os IDs e a data (via `save()` com o objeto modificado)

**Operações necessárias:**
- Método customizado para buscar pendentes (a ser definido conforme o critério confirmado na Fase 1)
- `save(DadosCpfEntity entity)` → herdado, usado para o update

### Onde criar os repositories

```
src/main/java/com/jucerja/consultaCpf/
    └── repository/
        ├── DadosCpfRepository.java
        ├── ConsultaCpfS09Repository.java
        └── SituacaoCadastralCpfRepository.java
```

### Cuidados importantes nesta fase

- **O tipo da chave primária no `JpaRepository<Entidade, TipoDaPK>`** deve corresponder exatamente ao tipo mapeado na entidade (`Long`, `Integer`, `UUID`). Se errar, o projeto não compila.
- **Nomes de métodos derivados** (query by method name) são sensíveis ao nome dos atributos da entidade Java, não às colunas do banco. Se o atributo na entidade se chama `consultaS09`, o método é `findByConsultaS09IsNull()`, não `findByConsultaS09IDIsNull()`.
- **Para queries mais complexas**, use `@Query` com JPQL (linguagem de consulta do JPA) ou SQL nativo. Para este projeto, a complexidade esperada é baixa.

---

## 10. Fase 5 — Integração com o Fluxo SOAP

### Objetivo

Adaptar o `CpfSoapClient` para que o XML bruto da resposta do serviço S09 seja acessível ao Service, permitindo sua persistência na tabela `ConsultaCPFS09`.

### Motivação

Hoje, o `CpfSoapClient.consultarCpf()` recebe a resposta XML, a desserializa em objeto Java e descarta o XML original. Com a nova demanda, o XML bruto deve ser salvo no banco. O método precisa ser alterado para retornar ambos: o XML bruto e o objeto desserializado.

### O problema atual em detalhe

Dentro do `CpfSoapClient`, acontece o seguinte:

```
ResponseEntity<String> response = restTemplate.exchange(...);
    ↓
String xmlBruto = response.getBody();  ← este valor é obtido aqui
    ↓
SoapEnvelope envelope = XmlParser.fromXml(xmlBruto, SoapEnvelope.class);  ← xml é usado aqui
    ↓
return envelope.getBody().getConsultaCPFResponse();  ← apenas o objeto é retornado
                                                        o xmlBruto é perdido aqui
```

### Como resolver

Duas abordagens principais:

**Abordagem A — Criar um objeto de resultado intermediário (recomendada):**

Criar uma classe (pode ser um `record` Java) que encapsule os dois valores: o XML bruto e o `ConsultaCPFResponse`. O método `consultarCpf()` passaria a retornar esse objeto em vez de apenas `ConsultaCPFResponse`. Isso é semanticamente mais claro e não polui a assinatura com dois retornos.

Estrutura conceitual:
```
SoapResult {
    String xmlBruto
    ConsultaCPFResponse response
}
```

**Abordagem B — Alterar o Service para aceitar XML e objeto separados:**

Menos elegante. O Service seria responsável por chamar o cliente de formas diferentes para obter cada parte. Não recomendado.

### Como o XML deverá ser persistido

O XML que deve ser salvo é o conteúdo bruto retornado pelo HTTP — `response.getBody()` — antes de qualquer processamento. Isso garante que o registro auditável seja fiel ao que a RFB realmente retornou.

> **Dúvida a confirmar com a área de negócio:** deve-se salvar o envelope SOAP completo (incluindo `<Envelope>` e `<Body>`) ou apenas o conteúdo interno do `<Body>`? Recomenda-se salvar o envelope completo para máxima fidelidade de auditoria.

### Onde a adaptação ocorre

**Arquivo alterado:** `CpfSoapClient.java`

**Arquivo possivelmente criado:** `dto/SoapResult.java` (ou `record SoapResult`)

### Cuidados nesta fase

- Não altere o comportamento da chamada SOAP em si — apenas o que é retornado pelo método
- O XML retornado pode estar comprimido (o header `Accept-Encoding: gzip,deflate` já está configurado). O `RestTemplate` com `HttpComponentsClientHttpRequestFactory` descomprime automaticamente, portanto `response.getBody()` já conterá o XML legível
- O XML pode conter caracteres especiais (acentos, XML declarations com encoding). Garanta que o encoding seja preservado ao salvar no banco

---

## 11. Fase 6 — Atualização dos Dados

### Objetivo

Criar o Service de processamento que orquestra o fluxo completo: lê o banco, chama o SOAP, salva o XML, localiza a situação cadastral e atualiza o registro do CPF.

### Motivação

Esta é a fase de maior valor — é aqui que todos os componentes criados nas fases anteriores se conectam e o requisito de negócio se torna realidade.

### Como localizar o registro correto a ser atualizado

O fluxo iterará sobre uma lista de `DadosCpfEntity` lida do banco. Para cada entidade:
1. O campo CPF é lido da entidade (`entity.getCpf()`)
2. Esse CPF é passado ao `CpfSoapClient`
3. Ao final do processamento, é a **mesma entidade** que foi lida que deverá ser atualizada — não é necessário buscá-la novamente

O update é feito diretamente no objeto já carregado: os campos são preenchidos e `repository.save(entity)` é chamado. O JPA sabe que é um update (não um insert) porque a entidade já tem uma chave primária preenchida.

### Como preencher os campos solicitados

**`CONSULTAS09ID`:** após o `consultaCpfS09Repository.save(novaConsulta)`, o JPA retorna o objeto com o ID gerado pelo banco preenchido. Esse ID é o valor que vai para o campo `CONSULTAS09ID` da entidade `DadosCpfEntity`.

**`SITUACAOCADASTRALRFBID`:** o código de situação vem da resposta SOAP (ex.: `"0"`). O Service busca `situacaoCadastralRepository.findByCodigo("0")` e obtém a entidade `SituacaoCadastralCpfEntity` correspondente. Essa entidade (o objeto inteiro, por causa do `@ManyToOne`) é atribuída ao campo `situacaoCadastral` da `DadosCpfEntity`.

**`DATAATUALIZACAO`:** `LocalDateTime.now()` ou `OffsetDateTime.now()`, dependendo do tipo da coluna confirmado na Fase 1.

### O que deve acontecer se um CPF falhar

Se a chamada SOAP falhar para um CPF específico (ex.: timeout, CPF inválido, erro no serviço externo), os demais CPFs da fila não devem ser afetados. O tratamento de erro deve:
1. Capturar a exceção para aquele CPF
2. Registrar o erro (log)
3. Continuar para o próximo CPF

Cada CPF deve ser processado em sua própria transação. Se a transação de um falhar, ela é revertida isoladamente sem afetar os outros.

### Sobre transações (`@Transactional`)

O processamento de cada CPF envolve duas operações de escrita no banco:
1. Insert em `ConsultaCPFS09`
2. Update em `DadosCPF`

Essas duas operações devem ocorrer na mesma transação. Se o update em `DadosCPF` falhar após o insert em `ConsultaCPFS09` já ter ocorrido, a transação deve ser revertida para evitar inconsistência (XML salvo sem o CPF atualizado).

A anotação `@Transactional` no método do Service garante esse comportamento: ou as duas operações ocorrem, ou nenhuma delas ocorre.

### Arquivo criado nesta fase

**`service/ProcessamentoCpfService.java`** — Service principal do novo fluxo. Contém:
- Injeção dos três repositories e do `CpfSoapClient`
- Método principal de processamento (anotado com `@Transactional` no escopo adequado)
- Tratamento de erros por CPF (try/catch individual)
- Lógica de montagem das entidades antes de salvar

### Adaptação do Controller

O `CpfController.java` precisará de um novo endpoint para disparar o processamento. Por convenção REST, operações que causam efeitos colaterais (escrita no banco) devem usar `POST`, não `GET`.

Exemplo de endpoint esperado:
```
POST /cpf/processar
```

O endpoint existente (`GET /cpf/{cpf}`) pode ser mantido para consultas pontuais e diagnóstico, mas o fluxo principal passa a ser o novo endpoint.

---

## 12. Fase 7 — Testes

### Objetivo

Garantir que o fluxo implementado funciona corretamente para os cenários de sucesso e que os erros são tratados de forma previsível e segura.

### O que deve ser testado

#### Testes de unidade — `ProcessamentoCpfServiceTest`

Testes de unidade isolam a classe sendo testada de suas dependências externas (banco, serviço SOAP). Isso é feito com **mocks** — objetos falsos que simulam o comportamento dos componentes reais.

**Cenário 1 — Fluxo feliz (sucesso completo):**
- Mock do `DadosCpfRepository` retorna uma lista com um CPF pendente
- Mock do `CpfSoapClient` retorna XML bruto + objeto com código de situação `"0"`
- Mock do `ConsultaCpfS09Repository.save()` retorna entidade com ID preenchido
- Mock do `SituacaoCadastralCpfRepository.findByCodigo("0")` retorna entidade de situação
- **Verificar:** que `DadosCpfRepository.save()` foi chamado com os campos corretos preenchidos

**Cenário 2 — Lista de CPFs vazia:**
- Mock do `DadosCpfRepository` retorna lista vazia
- **Verificar:** que nenhuma outra chamada é feita (SOAP não é chamado)

**Cenário 3 — Falha no SOAP para um CPF:**
- Mock do `CpfSoapClient` lança exceção para o primeiro CPF
- Lista tem dois CPFs
- **Verificar:** que o segundo CPF ainda é processado (o erro no primeiro não interrompe o fluxo)

**Cenário 4 — Situação cadastral não encontrada no banco:**
- Mock do `SituacaoCadastralCpfRepository.findByCodigo()` retorna `Optional.empty()` ou nulo
- **Verificar:** o tratamento adequado — lançar exceção? Pular atualização de situação? Registrar log?

**Cenário 5 — Resposta SOAP nula ou inválida:**
- Mock do `CpfSoapClient` retorna objeto com campos nulos
- **Verificar:** que o erro é capturado e tratado sem propagar para outros CPFs

#### Testes de integração — (opcional, mas recomendado)

Testes de integração verificam se as entidades JPA estão corretamente mapeadas e se as queries funcionam contra um banco real.

Uma opção é usar um banco H2 em memória para os testes (compatível com JPA, mas não com todas as features do SQL Server). Para máxima confiabilidade, o ideal é ter uma instância de SQL Server de teste.

**O que validar nos testes de integração:**
- As três entidades são corretamente criadas, lidas e atualizadas
- O `findByCodigo` da `SituacaoCadastralCpfRepository` retorna o registro correto
- O `save` na `ConsultaCpfS09Repository` gera um ID e persiste o XML
- O relacionamento `@ManyToOne` é resolvido corretamente ao salvar `DadosCpfEntity`

#### Validação manual no banco

Após rodar o processamento em ambiente de desenvolvimento:

1. Verificar na tabela `ConsultaCPFS09` se o XML foi inserido e se o conteúdo está correto e completo
2. Verificar na tabela `DadosCPF` se os três campos foram preenchidos: `SITUACAOCADASTRALRFBID`, `CONSULTAS09ID`, `DATAATUALIZACAO`
3. Verificar se os IDs das FKs apontam para os registros corretos nas tabelas de destino
4. Executar um SELECT com JOIN entre as três tabelas para validar a consistência referencial:

```sql
SELECT 
    dc.CPF,
    dc.DATAATUALIZACAO,
    sc.CODIGO AS cod_situacao,
    sc.DESCRICAO AS situacao,
    cs.ID AS id_consulta
FROM DadosCPF dc
LEFT JOIN SituacaoCadastralCPF sc ON dc.SITUACAOCADASTRALRFBID = sc.ID
LEFT JOIN ConsultaCPFS09 cs ON dc.CONSULTAS09ID = cs.ID
WHERE dc.DATAATUALIZACAO IS NOT NULL;
```

---

## 13. Possíveis Dificuldades

### 1. Nomes de colunas SQL Server vs. convenção Java

O SQL Server é historicamente utilizado com nomes de colunas em MAIÚSCULAS_COM_UNDERSCORE (ex.: `DATAATUALIZACAO`, `CONSULTAS09ID`). O Java usa camelCase (ex.: `dataAtualizacao`, `consultaS09Id`). O mapeamento explícito via `@Column(name = "NOME_COLUNA")` é obrigatório para evitar que o Hibernate tente adivinhar o nome errado.

### 2. Tipo da chave primária

Se a PK for um `UNIQUEIDENTIFIER` (UUID) no SQL Server — algo comum em sistemas corporativos — o tipo Java deverá ser `UUID`, e o JPA precisa de configuração específica de geração de ID (`@GeneratedValue(strategy = GenerationType.AUTO)` com UUID pode requerer ajuste).

### 3. Tamanho do XML no banco

O XML de resposta do SOAP pode ser extenso. Se a coluna de XML na tabela `ConsultaCPFS09` tiver limite de tamanho, XMLs maiores causarão falha ao salvar. Confirme o tipo da coluna — `VARCHAR(MAX)` no SQL Server suporta até 2 GB.

### 4. Encoding do XML

O XML começa com `<?xml version="1.0" encoding="UTF-8"?>`. Se o banco estiver configurado com collation diferente ou a coluna for `VARCHAR` (não `NVARCHAR`), caracteres especiais (acentos em nomes) podem ser corrompidos. Prefira `NVARCHAR(MAX)` para armazenamento de XML com caracteres latinos.

### 5. Transações e rollback parcial

O `@Transactional` no Spring, por padrão, faz rollback apenas para `RuntimeException` (unchecked). Se exceções checked forem lançadas sem `@Transactional(rollbackFor = Exception.class)`, o banco pode ter registros parcialmente persistidos. Atenção ao configurar o escopo transacional.

### 6. Lazy Loading fora de transação

O Spring Data JPA carrega relacionamentos `LAZY` somente dentro de uma sessão JPA ativa. Se uma entidade for retornada pelo repository e acessada fora da transação, acessar atributos `LAZY` causará `LazyInitializationException`. Solução: use `@Transactional` nos métodos do Service que acessam relacionamentos, ou use `JOIN FETCH` nas queries.

### 7. N+1 queries

Se o Service buscar uma lista de `DadosCpfEntity` e para cada elemento acessar os relacionamentos `@ManyToOne`, o JPA pode executar N queries adicionais (uma por entidade) além da query inicial. Para a lista de CPFs pendentes, use `JOIN FETCH` na query para carregar os relacionamentos necessários em uma só consulta.

### 8. Criação redundante de RestTemplate e HttpClient

O `CpfSoapClient` hoje cria um novo `HttpClient` e `RestTemplate` a cada chamada. Quando o fluxo processar N CPFs em sequência, isso se multiplica. Recomenda-se transformá-los em beans Spring para reuso, embora esta melhoria não seja bloqueante para a implementação funcionar.

### 9. Código de situação não mapeado na tabela

Se a RFB retornar um código de situação que não existe na tabela `SituacaoCadastralCPF` (ex.: um novo código que foi adicionado pela RFB e ainda não está na tabela), o `findByCodigo()` retornará nulo ou `Optional.empty()`. O fluxo deve tratar esse caso explicitamente para evitar `NullPointerException` ao tentar salvar a FK.

### 10. Credenciais em plain text no application.yaml

Colocar usuário e senha do banco diretamente no `application.yaml` é um risco de segurança se o arquivo for commitado no repositório Git. Avaliar o uso de variáveis de ambiente ou um serviço de secrets antes de subir para qualquer ambiente compartilhado.

---

## 14. Decisões Arquiteturais Recomendadas

### Spring Data JPA — Recomendado: Sim

**Justificativa:** é o padrão de mercado no ecossistema Spring para acesso a banco relacional com mapeamento objeto-relacional. Elimina a necessidade de escrever SQL para operações comuns, integra nativamente com o gerenciamento de transações do Spring (`@Transactional`) e reduz drasticamente o código de infraestrutura. Para um projeto de porte pequeno/médio como este, é a escolha mais produtiva e segura.

Alternativa descartada: JDBC puro (JdbcTemplate) seria mais verboso e não traria benefícios para este caso de uso.

### Flyway — Recomendado: Não imediatamente, mas planejado para o futuro

**Justificativa:** como as tabelas já existem no banco, o Flyway não é necessário para esta implementação inicial. Adicioná-lo agora sem scripts de migração retroativos pode causar confusão.

No entanto, para qualquer evolução futura do schema (nova coluna, novo índice, ajuste de tipo), o Flyway é fortemente recomendado. Sugestão: adicionar como "débito técnico planejado" logo após a implementação inicial estabilizar.

**O que não usar:** `spring.jpa.hibernate.ddl-auto=update` ou `create`. Essas opções foram criadas para desenvolvimento local e nunca devem ser usadas em um banco com dados reais. Use `validate` (verifica se o schema bate com as entidades) ou `none`.

### DTOs para respostas da API — Recomendado: Sim

**Justificativa:** o Controller atualmente retorna `String` diretamente. Com a integração ao banco, o processamento pode retornar informações mais ricas (CPFs processados, erros, tempo de execução). Um DTO dedicado permite evoluir o contrato da API sem quebrar clientes existentes.

Sugestão: criar um DTO simples de resposta de processamento com campos como `cpfsProcessados`, `cpfsComErro`, `mensagem`.

### Service adicional de processamento — Recomendado: Sim

**Justificativa:** o `CpfService` atual tem uma responsabilidade bem definida: orquestrar uma consulta pontual. Misturar nele a lógica de leitura do banco, persistência do XML e atualização de registros tornaria a classe excessivamente grande e com responsabilidades misturadas (violação do Princípio da Responsabilidade Única — SRP do SOLID).

A criação de um `ProcessamentoCpfService` separado mantém cada classe focada e torna o código mais fácil de testar, manter e entender. O `CpfService` existente pode até ser reutilizado internamente pelo novo Service para a parte de tradução de código, evitando duplicação.

### Tratamento centralizado de erros — Recomendado: Sim

**Justificativa:** hoje o Controller retorna String e o Service lança `RuntimeException`. Com a integração ao banco, erros mais ricos precisam ser comunicados ao chamador. Um `@RestControllerAdvice` com `@ExceptionHandler` permitiria retornar respostas JSON padronizadas para erros, sem poluir os Controllers com lógica de tratamento.

---

## 15. Checklist de Implementação

Use esta lista durante o desenvolvimento para acompanhar o progresso. Marque cada item conforme concluído.

### Fase 1 — Análise do Banco
```
[ ] Obter e executar DDLs das três tabelas no SQL Server
[ ] Documentar todas as colunas de DadosCPF (nome, tipo, nullable, PK/FK)
[ ] Documentar todas as colunas de ConsultaCPFS09 (nome, tipo, nullable, PK/FK)
[ ] Documentar todas as colunas de SituacaoCadastralCPF (nome, tipo, nullable, PK)
[ ] Confirmar o tipo da chave primária de cada tabela (INT, BIGINT, UUID?)
[ ] Confirmar relacionamentos via FK (quais colunas referenciam quais)
[ ] Confirmar o critério de "CPF pendente" (CONSULTAS09ID nulo? Flag? Outro?)
[ ] Verificar conteúdo atual da tabela SituacaoCadastralCPF (está populada?)
[ ] Verificar se os códigos 6 e 7 da RFB existem na tabela de situação
[ ] Confirmar tipo da coluna de XML em ConsultaCPFS09 (VARCHAR(MAX)? NVARCHAR(MAX)? XML?)
[ ] Confirmar se DATA_CONSULTA em ConsultaCPFS09 é preenchida pela app ou pelo banco (DEFAULT)
[ ] Documentar o resultado desta fase antes de avançar
```

### Fase 2 — Configuração de Banco de Dados
```
[ ] Adicionar spring-boot-starter-data-jpa ao pom.xml
[ ] Adicionar dependência do driver JDBC SQL Server (mssql-jdbc) ao pom.xml
[ ] Adicionar bloco spring.datasource ao application.yaml com URL, usuário e senha
[ ] Configurar spring.jpa.hibernate.ddl-auto=validate
[ ] Configurar spring.jpa.show-sql=true (temporariamente)
[ ] Rodar a aplicação e verificar que sobe sem erros de conexão
[ ] Confirmar no log que o Hibernate se conectou ao banco
[ ] Confirmar que o endpoint GET /cpf/{cpf} existente ainda funciona normalmente
```

### Fase 3 — Mapeamento das Entidades
```
[ ] Criar pacote entity/ em src/main/java/com/jucerja/consultaCpf/
[ ] Criar SituacaoCadastralCpfEntity com @Entity, @Table, @Id e campos mapeados
[ ] Criar ConsultaCpfS09Entity com @Entity, @Table, @Id e campos mapeados
[ ] Criar DadosCpfEntity com @Entity, @Table, @Id e campos mapeados
[ ] Mapear @ManyToOne de DadosCpfEntity para SituacaoCadastralCpfEntity
[ ] Mapear @ManyToOne de DadosCpfEntity para ConsultaCpfS09Entity
[ ] Verificar que os nomes das colunas nas anotações batem com o banco real
[ ] Rodar a aplicação com ddl-auto=validate e confirmar que o Hibernate não lança erros de schema
```

### Fase 4 — Camada de Persistência
```
[ ] Criar pacote repository/ em src/main/java/com/jucerja/consultaCpf/
[ ] Criar SituacaoCadastralCpfRepository estendendo JpaRepository
[ ] Declarar método findByCodigo (ou equivalente) em SituacaoCadastralCpfRepository
[ ] Criar ConsultaCpfS09Repository estendendo JpaRepository
[ ] Criar DadosCpfRepository estendendo JpaRepository
[ ] Declarar método para buscar CPFs pendentes em DadosCpfRepository
[ ] Escrever um teste simples (ou verificação manual) de que findByCodigo retorna o registro correto
[ ] Escrever um teste simples de que save em ConsultaCpfS09Repository persiste e retorna ID
```

### Fase 5 — Integração com o Fluxo SOAP
```
[ ] Criar classe/record SoapResult (ou equivalente) com campos xmlBruto e response
[ ] Alterar CpfSoapClient.consultarCpf() para retornar SoapResult em vez de ConsultaCPFResponse
[ ] Verificar que o xmlBruto em SoapResult contém o XML completo e correto
[ ] Confirmar que o encoding do XML está preservado (testar com CPF cujo nome tem acentos)
[ ] Ajustar CpfService.verificarSituacao() para extrair o ConsultaCPFResponse do SoapResult
[ ] Confirmar que o endpoint GET /cpf/{cpf} existente continua funcionando após a alteração
```

### Fase 6 — Atualização dos Dados
```
[ ] Criar pacote (se necessário) para o novo Service
[ ] Criar ProcessamentoCpfService com injeção dos repositories e do CpfSoapClient
[ ] Implementar método principal de processamento
[ ] Implementar leitura de CPFs pendentes via DadosCpfRepository
[ ] Implementar chamada ao CpfSoapClient para cada CPF
[ ] Implementar criação e save de ConsultaCpfS09Entity com o XML bruto
[ ] Implementar busca da SituacaoCadastralCpfEntity pelo código retornado
[ ] Implementar atualização de DadosCpfEntity com os três campos solicitados
[ ] Anotar o método com @Transactional no escopo adequado
[ ] Implementar tratamento de erro por CPF (try/catch individual)
[ ] Adicionar logs informativos (substituir System.out.println por log.info)
[ ] Criar novo endpoint POST /cpf/processar no CpfController
[ ] Testar o fluxo completo manualmente com um CPF real do banco
[ ] Verificar no banco que ConsultaCPFS09 tem o XML inserido
[ ] Verificar no banco que DadosCPF foi atualizado com os três campos
[ ] Verificar integridade referencial: FKs apontam para os registros corretos
[ ] Executar SELECT com JOIN entre as três tabelas e validar consistência
```

### Fase 7 — Testes
```
[ ] Criar ProcessamentoCpfServiceTest com mocks dos repositories e do SoapClient
[ ] Implementar teste do cenário: fluxo feliz (sucesso completo)
[ ] Implementar teste do cenário: lista de CPFs vazia
[ ] Implementar teste do cenário: falha no SOAP para um CPF (os demais continuam)
[ ] Implementar teste do cenário: código de situação não encontrado na tabela
[ ] Implementar teste do cenário: resposta SOAP nula ou inválida
[ ] Rodar todos os testes e confirmar que passam
[ ] Verificar cobertura mínima do ProcessamentoCpfService
[ ] Realizar teste de carga básico: processar 10+ CPFs em sequência e validar banco
```

### Validação Final
```
[ ] Revisar todos os logs da aplicação — não deve haver System.out.println remanescentes
[ ] Confirmar que ddl-auto está em validate (nunca create/update) no ambiente de homologação
[ ] Confirmar que credenciais de banco não estão commitadas no repositório Git
[ ] Atualizar DOCUMENTACAO.md para refletir o novo fluxo
[ ] Apresentar o fluxo completo para a área de negócio com evidência de dados no banco
```

---

*Este documento é o guia oficial de implementação e deve ser consultado em cada fase do desenvolvimento. Atualize-o conforme decisões forem tomadas ou dúvidas forem respondidas.*
