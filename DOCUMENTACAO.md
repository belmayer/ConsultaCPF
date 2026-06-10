# Documentação Técnica — ConsultaCPF

> Gerado em: 10/06/2026  
> Versão analisada: 0.0.1-SNAPSHOT  
> Destinado a: desenvolvedores que estão entrando no projeto agora

---

## Sumário

1. [O que é esta aplicação?](#1-o-que-é-esta-aplicação)
2. [Visão geral da arquitetura](#2-visão-geral-da-arquitetura)
3. [Estrutura de pastas](#3-estrutura-de-pastas)
4. [Dependências e tecnologias](#4-dependências-e-tecnologias)
5. [Configuração da aplicação](#5-configuração-da-aplicação)
6. [Camadas da aplicação — detalhe por arquivo](#6-camadas-da-aplicação--detalhe-por-arquivo)
   - [Ponto de entrada](#61-ponto-de-entrada)
   - [Controller](#62-controller--cpfcontrollerjava)
   - [Service](#63-service--cpfservicejava)
   - [Client SOAP](#64-client-soap--cpfsoapclientjava)
   - [Envelope SOAP (Request)](#65-envelope-soap--soaenvelopejava-e-soapbodyjava)
   - [Request SOAP](#66-request-soap--consultacpfrequestjava)
   - [Response SOAP](#67-response-soap--consultacpfresponsejava-retornows09redesimjava-dadoscpfjava)
   - [Utilitário XML](#68-utilitário-xml--xmlparserjava)
   - [Frontend](#69-frontend--indexhtml-scriptjs-stylecss)
   - [Testes](#610-testes--consultacpfapplicationtestsjava)
7. [Fluxo completo de uma requisição](#7-fluxo-completo-de-uma-requisição)
8. [A mensagem SOAP em detalhe](#8-a-mensagem-soap-em-detalhe)
9. [Mapeamento dos códigos de situação cadastral](#9-mapeamento-dos-códigos-de-situação-cadastral)
10. [Estado atual do projeto](#10-estado-atual-do-projeto)

---

## 1. O que é esta aplicação?

Esta aplicação é um **serviço de consulta de CPF** desenvolvido para a **JUCERJA** (Junta Comercial do Estado do Rio de Janeiro).

O objetivo é simples: dado um número de CPF, a aplicação consulta a **Receita Federal do Brasil (RFB)** através de um serviço SOAP intermediado pelo **SERPRO** (Serviço Federal de Processamento de Dados) e devolve a **situação cadastral** daquele CPF — se está Regular, Suspenso, Cancelado, etc.

A aplicação é composta por:
- Uma **interface web simples** (HTML + JavaScript) onde o usuário digita o CPF
- Um **backend Java com Spring Boot** que recebe a requisição, chama o serviço externo e devolve o resultado

---

## 2. Visão geral da arquitetura

```
┌──────────────────────────────────────────────────────────────────────┐
│                          USUÁRIO (Navegador)                         │
│                                                                      │
│   Acessa http://localhost:8081  →  digita CPF  →  clica "Consultar" │
└────────────────────────────┬─────────────────────────────────────────┘
                             │ GET /cpf/{numeroCPF}
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│                     SPRING BOOT APPLICATION                          │
│                        (porta 8081)                                  │
│                                                                      │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────────┐   │
│  │ CpfController│───▶│  CpfService  │───▶│   CpfSoapClient      │   │
│  │  (REST API)  │    │  (regras de  │    │  (chamada HTTP/SOAP)  │   │
│  │              │◀───│   negócio)   │◀───│                      │   │
│  └──────────────┘    └──────────────┘    └──────────┬───────────┘   │
│                                                     │               │
│          XmlParser (serializa/desserializa XML) ◀───┘               │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
                             │ POST SOAP (HTTP)
                             ▼
┌──────────────────────────────────────────────────────────────────────┐
│            SERVIÇO EXTERNO — SERPRO / RFB (Homologação)              │
│   http://rfb.hml.jucerja.rj.gov.br/services/ws09/ws09               │
│                                                                      │
│   Recebe envelope SOAP 1.2 com o CPF e devolve dados cadastrais     │
└──────────────────────────────────────────────────────────────────────┘
```

**Padrão arquitetural utilizado:** MVC em camadas (Controller → Service → Client), sem banco de dados local — todos os dados vêm do serviço externo.

---

## 3. Estrutura de pastas

```
ConsultaCPF/                                ← raiz do repositório
└── consultaCpf/                            ← módulo Maven
    ├── pom.xml                             ← configuração do projeto e dependências
    ├── mvnw / mvnw.cmd                     ← Maven Wrapper (roda Maven sem instalar)
    ├── .gitignore
    ├── .gitattributes
    ├── .mvn/
    │   └── wrapper/
    │       └── maven-wrapper.properties    ← versão do Maven usada pelo wrapper
    └── src/
        ├── main/
        │   ├── java/
        │   │   └── com/jucerja/consultaCpf/
        │   │       ├── ConsultaCpfApplication.java       ← ponto de entrada (main)
        │   │       ├── controller/
        │   │       │   └── CpfController.java            ← recebe requisições HTTP
        │   │       ├── service/
        │   │       │   └── CpfService.java               ← regras de negócio
        │   │       ├── client/
        │   │       │   └── CpfSoapClient.java            ← chama o web service externo
        │   │       ├── util/
        │   │       │   └── XmlParser.java                ← converte objetos ↔ XML
        │   │       └── soap/
        │   │           ├── envelope/
        │   │           │   ├── SoapEnvelope.java         ← estrutura raiz do SOAP
        │   │           │   └── SoapBody.java             ← corpo do envelope SOAP
        │   │           ├── request/
        │   │           │   └── ConsultaCPFRequest.java   ← dados enviados ao SOAP
        │   │           └── response/
        │   │               ├── ConsultaCPFResponse.java  ← envelope da resposta SOAP
        │   │               ├── RetornoWS09Redesim.java   ← dados internos da resposta
        │   │               └── DadosCPF.java             ← dados do CPF consultado
        │   └── resources/
        │       ├── application.yaml                      ← configuração da aplicação
        │       └── static/                               ← arquivos servidos pelo Spring
        │           ├── index.html                        ← interface do usuário
        │           ├── script.js                         ← lógica do frontend
        │           └── style.css                         ← estilo visual
        └── test/
            └── java/
                └── com/jucerja/consultaCpf/
                    └── ConsultaCpfApplicationTests.java  ← teste básico de contexto
```

---

## 4. Dependências e tecnologias

Definidas no `pom.xml`:

| Dependência | Para que serve |
|---|---|
| `spring-boot-starter-webmvc` | Habilita o servidor web (Tomcat embutido) e suporte a controllers REST |
| `spring-boot-starter-webservices` | Suporte a SOAP/XML no Spring — traz JAXB e outras bibliotecas XML |
| `org.projectlombok:lombok` | Gera automaticamente getters, setters e outros métodos durante a compilação, reduzindo código repetitivo |
| `spring-boot-starter-webmvc-test` | Suporte a testes de controllers (MockMvc) |
| `spring-boot-starter-webservices-test` | Suporte a testes de web services |
| `httpclient5` | Apache HttpClient 5 — cliente HTTP de baixo nível usado pelo `RestTemplate` para fazer as chamadas ao serviço SOAP |

**Versões principais:**
- Java: **21**
- Spring Boot: **4.0.6**
- Maven (wrapper): configurado em `maven-wrapper.properties`

**O que é Lombok?**  
Lombok é uma biblioteca que funciona durante a compilação. Quando você vê `@Getter` e `@Setter` em uma classe, o Lombok gera automaticamente os métodos `getNomeDoAtributo()` e `setNomeDoAtributo()` sem você precisar escrever. Isso mantém as classes de modelo mais limpas.

**O que é JAXB?**  
JAXB (Jakarta XML Binding) é a tecnologia responsável por converter objetos Java em XML (marshalling) e XML de volta em objetos Java (unmarshalling). Ela usa as anotações `@XmlRootElement`, `@XmlElement`, `@XmlAccessorType` que aparecem em todas as classes da pasta `soap/`.

---

## 5. Configuração da aplicação

**Arquivo:** `src/main/resources/application.yaml`

```yaml
spring:
  application:
    name: consultaCpf   # nome da aplicação no contexto Spring

server:
  port: 8081            # porta onde o servidor HTTP sobe
```

Configuração mínima. A aplicação roda na porta **8081**, ou seja, acessa-se pelo endereço `http://localhost:8081`.

---

## 6. Camadas da aplicação — detalhe por arquivo

### 6.1 Ponto de entrada

**Arquivo:** `ConsultaCpfApplication.java`

```
Responsabilidade: inicializar toda a aplicação Spring Boot.
```

```java
@SpringBootApplication
public class ConsultaCpfApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsultaCpfApplication.class, args);
    }
}
```

A anotação `@SpringBootApplication` é uma combinação de três anotações:
- `@Configuration` — esta classe pode definir beans Spring
- `@EnableAutoConfiguration` — Spring configura automaticamente o que detectar no classpath
- `@ComponentScan` — Spring varre o pacote atual e subpacotes procurando componentes (`@Controller`, `@Service`, `@Component`, etc.)

É o ponto de partida: ao executar `mvnw spring-boot:run`, o JVM chama este `main()`, que inicializa o Spring, sobe o Tomcat embutido na porta 8081 e deixa a aplicação pronta para receber requisições.

---

### 6.2 Controller — `CpfController.java`

```
Responsabilidade: receber requisições HTTP do mundo externo e delegar ao Service.
Camada: apresentação / entrada da aplicação.
```

```java
@RestController
@RequestMapping("/cpf")
public class CpfController {

    @Autowired
    private CpfService service;

    @GetMapping("/{cpf}")
    public String verificar(@PathVariable String cpf) {
        return service.verificarSituacao(cpf);
    }
}
```

**O que cada anotação significa:**

- `@RestController` — combina `@Controller` + `@ResponseBody`. Diz que esta classe atende requisições HTTP e que o retorno dos métodos será serializado diretamente no corpo da resposta HTTP (neste caso, como texto simples).
- `@RequestMapping("/cpf")` — todos os endpoints desta classe começam com `/cpf`.
- `@GetMapping("/{cpf}")` — responde a requisições HTTP GET em `/cpf/{qualquer-valor}`. O `{cpf}` é uma variável de caminho (path variable).
- `@PathVariable String cpf` — extrai o valor `{cpf}` da URL e injeta na variável local `cpf`.
- `@Autowired` — o Spring injeta automaticamente uma instância de `CpfService` (injeção de dependência).

**Exemplo de uso:**  
Ao acessar `GET http://localhost:8081/cpf/12345678900`, o valor `"12345678900"` é capturado e repassado para `service.verificarSituacao("12345678900")`.

**Relacionamento:** recebe do usuário → passa para `CpfService` → devolve o resultado como texto HTTP.

---

### 6.3 Service — `CpfService.java`

```
Responsabilidade: orquestrar a lógica de negócio — chamar o cliente SOAP,
                  validar a resposta e traduzir o código de situação.
Camada: negócio.
```

Este é o arquivo mais rico em lógica. Ele faz quatro coisas principais:

**1. Chamar o cliente SOAP:**
```java
ConsultaCPFResponse response = soapClient.consultarCpf(cpf);
```

**2. Validar a resposta em cascata (guardas de nulidade):**
```java
if (response == null)                          → "Resposta SOAP nula"
if (response.getRetornoWS09Redesim() == null)  → "Retorno WS vazio"
if (listaCpf == null || listaCpf.isEmpty())    → "CPF não encontrado"
```

**3. Extrair o primeiro resultado:**
```java
DadosCPF dadosCPF = listaCpf.get(0);
String codigoSituacao = dadosCPF.getSituacaoCadastral();
```

O serviço SOAP pode retornar uma lista de CPFs (note `List<DadosCPF>`), mas apenas o primeiro é utilizado.

**4. Traduzir o código numérico em descrição legível:**
```java
private String traduzirSituacao(String codigo) {
    return switch (codigo) {
        case "0" -> "Regular";
        case "1" -> "Cancelada por encerramento de espólio";
        case "2" -> "Suspensa";
        case "3" -> "Cancelada óbito sem espólio";
        case "4" -> "Pendente de Regularização";
        case "5" -> "Cancelada multiplicidade";
        case "8" -> "Nula";
        case "9" -> "Cancelada de ofício";
        default  -> "Situação desconhecida";
    };
}
```

Também há quatro chamadas `System.out.println()` para fins de depuração — exibem no console o CPF, nome, código e descrição da situação.

**Relacionamento:** recebe do `CpfController` → chama `CpfSoapClient` → usa `DadosCPF`/`ConsultaCPFResponse`/`RetornoWS09Redesim` → devolve String ao Controller.

---

### 6.4 Client SOAP — `CpfSoapClient.java`

```
Responsabilidade: construir a mensagem SOAP, enviá-la via HTTP e deserializar
                  a resposta de volta para objetos Java.
Camada: infraestrutura / integração externa.
```

Este componente encapsula toda a complexidade da comunicação com o serviço externo. Veja o passo a passo do que ele faz dentro do método `consultarCpf(String cpf)`:

**Passo 1 — Monta o objeto de requisição:**
```java
ConsultaCPFRequest consultaRequest = new ConsultaCPFRequest();
consultaRequest.setCodServico("S09");
consultaRequest.setVersao("100000");
consultaRequest.setNumeroProtocolo("RJP1234567890");  // hardcoded
consultaRequest.setNumeroOcorrencia(1);
consultaRequest.setCodEvento("101");
consultaRequest.setCpf(cpf);  // único valor dinâmico
```

**Passo 2 — Envolve em um envelope SOAP:**
```java
SoapBody body = new SoapBody();
body.setConsultaCPFRequest(consultaRequest);

SoapEnvelope envelope = new SoapEnvelope();
envelope.setBody(body);
```

**Passo 3 — Converte para XML:**
```java
String xml = XmlParser.toXml(envelope);
```

**Passo 4 — Configura o cliente HTTP:**
```java
HttpClient httpClient = HttpClients.createDefault();
HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
RestTemplate restTemplate = new RestTemplate(factory);
```

**Passo 5 — Define os cabeçalhos HTTP:**
```java
headers.set("Content-Type",
    "application/soap+xml;charset=UTF-8;" +
    "action=\"/ws09-service0.serviceagent/WS09Endpoint0/consultaCPF\""
);
headers.set("Accept-Encoding", "gzip,deflate");
```

O `Content-Type: application/soap+xml` indica que estamos usando **SOAP 1.2** (ao contrário de SOAP 1.1 que usa `text/xml`). O atributo `action` na frente indica qual operação do web service está sendo chamada.

**Passo 6 — Faz a chamada HTTP POST:**
```java
ResponseEntity<String> response = restTemplate.exchange(URL, HttpMethod.POST, request, String.class);
```

**Passo 7 — Desserializa a resposta XML:**
```java
SoapEnvelope responseEnvelope = XmlParser.fromXml(response.getBody(), SoapEnvelope.class);
return responseEnvelope.getBody().getConsultaCPFResponse();
```

**URL do serviço externo (hardcoded):**
```
http://rfb.hml.jucerja.rj.gov.br/services/ws09/ws09
```
O segmento `hml` indica que este é o **ambiente de homologação** (testes), não produção.

**Tratamento de erros:** dois blocos `catch` — um para erros HTTP com código de status (ex.: 500, 403) e outro genérico para qualquer outra exceção. Ambos relançam como `RuntimeException`.

---

### 6.5 Envelope SOAP — `SoapEnvelope.java` e `SoapBody.java`

```
Responsabilidade: representar a estrutura hierárquica de uma mensagem SOAP em Java.
```

O padrão SOAP define que toda mensagem deve ter esta estrutura XML:

```xml
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    <soap:Body>
        <!-- conteúdo da mensagem aqui -->
    </soap:Body>
</soap:Envelope>
```

**`SoapEnvelope.java`** mapeia o elemento raiz `<Envelope>`:
```java
@XmlRootElement(name = "Envelope", namespace = "http://www.w3.org/2003/05/soap-envelope")
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapEnvelope {
    @XmlElement(name = "Body", namespace = "http://www.w3.org/2003/05/soap-envelope")
    private SoapBody body;
}
```

**`SoapBody.java`** mapeia o elemento `<Body>` e pode conter tanto o request quanto o response:
```java
@XmlAccessorType(XmlAccessType.FIELD)
public class SoapBody {
    @XmlElement(namespace = "http://servicos.integrador.serpro.gov.br/")
    private ConsultaCPFRequest consultaCPFRequest;

    @XmlElement(namespace = "http://servicos.integrador.serpro.gov.br/")
    private ConsultaCPFResponse consultaCPFResponse;
}
```

O `SoapBody` contém os dois campos — o de requisição e o de resposta — porque a mesma classe é reutilizada tanto para serializar o envelope de saída (usando `consultaCPFRequest`) quanto para desserializar o envelope de entrada (usando `consultaCPFResponse`). Dependendo do contexto, apenas um dos campos estará preenchido.

**`@XmlAccessorType(XmlAccessType.FIELD)`** instrui o JAXB a mapear diretamente pelos atributos da classe (fields), e não pelos métodos getter/setter.

---

### 6.6 Request SOAP — `ConsultaCPFRequest.java`

```
Responsabilidade: representar os dados enviados ao serviço SOAP da RFB.
```

```java
@XmlRootElement(name = "consultaCPFRequest", namespace = "http://servicos.integrador.serpro.gov.br/")
public class ConsultaCPFRequest {
    private String codServico;       // código do serviço: "S09"
    private String versao;           // versão do serviço: "100000"
    private String numeroProtocolo;  // número do protocolo: "RJP1234567890"
    private Integer numeroOcorrencia;// ocorrência: 1
    private String codEvento;        // código do evento: "101"
    private String cpf;              // o CPF consultado (valor dinâmico)
}
```

Estes campos são definidos pelo contrato do web service do SERPRO. O único campo variável é `cpf` — todos os demais são valores fixos definidos no `CpfSoapClient`.

---

### 6.7 Response SOAP — `ConsultaCPFResponse.java`, `RetornoWS09Redesim.java`, `DadosCPF.java`

```
Responsabilidade: representar os dados recebidos do serviço SOAP da RFB.
```

A resposta do serviço tem três níveis de profundidade:

```
ConsultaCPFResponse
    └── RetornoWS09Redesim
            └── List<DadosCPF>
                    ├── numCPF
                    ├── nome
                    ├── dataNascimento
                    └── situacaoCadastral
```

**`ConsultaCPFResponse.java`** — envelope da resposta:
```java
@XmlRootElement(name = "consultaCPFResponse", namespace = "http://servicos.integrador.serpro.gov.br/")
public class ConsultaCPFResponse {
    private RetornoWS09Redesim retornoWS09Redesim;
}
```

**`RetornoWS09Redesim.java`** — objeto interno com metadados e a lista de CPFs:
```java
public class RetornoWS09Redesim {
    private String codServico;        // código do serviço retornado
    private String versao;            // versão
    @XmlElement(name = "dadosCPF")
    private List<DadosCPF> dadosCPF;  // lista de resultados
}
```

Atenção: `dadosCPF` é uma `List<>` — o serviço pode retornar múltiplos resultados para um mesmo CPF (embora na prática seja sempre um).

**`DadosCPF.java`** — dados de cada CPF retornado:
```java
public class DadosCPF {
    private String numCPF;           // número do CPF
    private String nome;             // nome do titular
    private String dataNascimento;   // data de nascimento
    private String situacaoCadastral;// código da situação (0, 1, 2, 3, 4, 5, 8, 9)
}
```

---

### 6.8 Utilitário XML — `XmlParser.java`

```
Responsabilidade: converter objetos Java em XML e XML em objetos Java (via JAXB).
Tipo: classe utilitária estática — não é um Bean Spring, não tem estado.
```

Dois métodos estáticos:

**`toXml(Object object)`** — serialização (objeto → XML):
```
objeto Java → JAXBContext → Marshaller → StringWriter → String XML
```

**`fromXml(String xml, Class<T> clazz)`** — desserialização (XML → objeto):
```
String XML → StringReader → JAXBContext → Unmarshaller → objeto Java tipado
```

Ambos criam um `JAXBContext` a cada chamada (não há cache). Qualquer falha lança `RuntimeException` com mensagem descritiva.

Esta classe é usada exclusivamente pelo `CpfSoapClient`:
- `XmlParser.toXml(envelope)` antes de enviar a requisição
- `XmlParser.fromXml(response.getBody(), SoapEnvelope.class)` ao receber a resposta

---

### 6.9 Frontend — `index.html`, `script.js`, `style.css`

```
Responsabilidade: interface visual para o usuário digitar o CPF e ver o resultado.
Localização: src/main/resources/static/
```

O Spring Boot serve automaticamente qualquer arquivo colocado em `src/main/resources/static/` — não é preciso nenhuma configuração adicional. O arquivo `index.html` fica disponível em `http://localhost:8081/`.

**`index.html`** — estrutura da página:
- Um campo `<input>` para digitar o CPF
- Um `<button>` que chama a função JavaScript `consultarCPF()`
- Uma `<div id="resultado">` onde o resultado é exibido

**`script.js`** — lógica do frontend:
```javascript
async function consultarCPF() {
    const cpf = document.getElementById("cpf").value;
    const resposta = await fetch(`/cpf/${cpf}`);    // chama a API REST
    const texto = await resposta.text();
    document.getElementById("resultado").innerHTML = texto;
}
```
Usa a API `fetch` nativa do navegador para fazer uma requisição GET ao backend. O resultado (texto simples) é injetado diretamente no HTML da página.

**`style.css`** — estilização básica:
- Centraliza o conteúdo na tela com Flexbox
- Adiciona um cartão branco com sombra
- Estiliza input e botão para largura total

---

### 6.10 Testes — `ConsultaCpfApplicationTests.java`

```
Responsabilidade: verificar que o contexto Spring sobe sem erros.
```

```java
@SpringBootTest
class ConsultaCpfApplicationTests {
    @Test
    void contextLoads() {
        // sem assertions — só verifica que a aplicação inicializa
    }
}
```

O único teste existente verifica que a aplicação consegue inicializar (carregar o contexto Spring) sem lançar exceções. Não testa nenhuma funcionalidade de negócio.

---

## 7. Fluxo completo de uma requisição

A seguir, um rastreamento passo a passo do que acontece quando o usuário digita `12345678900` e clica em "Consultar":

```
NAVEGADOR
    │
    │  1. Usuário clica "Consultar"
    │     script.js executa: fetch('/cpf/12345678900')
    │
    ▼
HTTP GET /cpf/12345678900
    │
    ▼
CpfController.verificar("12345678900")
    │
    │  2. Repassa para o Service
    ▼
CpfService.verificarSituacao("12345678900")
    │
    │  3. Delega ao Client SOAP
    ▼
CpfSoapClient.consultarCpf("12345678900")
    │
    │  4. Cria ConsultaCPFRequest com campos fixos + cpf="12345678900"
    │  5. Envolve em SoapBody → SoapEnvelope
    │  6. XmlParser.toXml(envelope) → String XML
    │  7. Configura HttpClient + RestTemplate
    │  8. Define headers SOAP 1.2
    │
    │  POST http://rfb.hml.jucerja.rj.gov.br/services/ws09/ws09
    │  Body: <?xml version="1.0"?><soap:Envelope>...</soap:Envelope>
    ▼
SERVIÇO EXTERNO (SERPRO/RFB)
    │
    │  9. Processa a consulta e devolve resposta XML
    ▼
CpfSoapClient (continua)
    │
    │  10. Recebe ResponseEntity<String> com XML da resposta
    │  11. XmlParser.fromXml(xml, SoapEnvelope.class) → SoapEnvelope
    │  12. Extrai: envelope.getBody().getConsultaCPFResponse()
    │  13. Retorna ConsultaCPFResponse
    ▼
CpfService (continua)
    │
    │  14. Valida: response não nulo?  ✓
    │  15. Valida: retornoWS09Redesim não nulo?  ✓
    │  16. Valida: lista dadosCPF não vazia?  ✓
    │  17. Pega dadosCPF.get(0)
    │  18. Lê situacaoCadastral (ex: "0")
    │  19. traduzirSituacao("0") → "Regular"
    │  20. System.out.println(...) [debug no console]
    │  21. Retorna "Regular"
    ▼
CpfController
    │
    │  22. Recebe "Regular" e devolve como corpo HTTP 200
    ▼
HTTP Response 200 OK
Body: "Regular"
    │
    ▼
NAVEGADOR
    │
    │  23. fetch() recebe a resposta
    │  24. resultado.innerHTML = "Regular"
    │  25. Usuário vê "Regular" na tela
```

---

## 8. A mensagem SOAP em detalhe

Para entender melhor o que trafega na rede, veja como fica a **mensagem de requisição** após a serialização pelo `XmlParser.toXml()`:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    <soap:Body>
        <ns2:consultaCPFRequest xmlns:ns2="http://servicos.integrador.serpro.gov.br/">
            <codServico>S09</codServico>
            <versao>100000</versao>
            <numeroProtocolo>RJP1234567890</numeroProtocolo>
            <numeroOcorrencia>1</numeroOcorrencia>
            <codEvento>101</codEvento>
            <cpf>12345678900</cpf>
        </ns2:consultaCPFRequest>
    </soap:Body>
</soap:Envelope>
```

E a **mensagem de resposta** esperada do serviço teria estrutura similar a:

```xml
<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    <soap:Body>
        <ns2:consultaCPFResponse xmlns:ns2="http://servicos.integrador.serpro.gov.br/">
            <retornoWS09Redesim>
                <codServico>S09</codServico>
                <versao>100000</versao>
                <dadosCPF>
                    <numCPF>12345678900</numCPF>
                    <nome>FULANO DE TAL</nome>
                    <dataNascimento>01/01/1980</dataNascimento>
                    <situacaoCadastral>0</situacaoCadastral>
                </dadosCPF>
            </retornoWS09Redesim>
        </ns2:consultaCPFResponse>
    </soap:Body>
</soap:Envelope>
```

---

## 9. Mapeamento dos códigos de situação cadastral

Tabela de referência dos códigos definidos no `CpfService.traduzirSituacao()`:

| Código | Descrição |
|--------|-----------|
| `0` | Regular |
| `1` | Cancelada por encerramento de espólio |
| `2` | Suspensa |
| `3` | Cancelada óbito sem espólio |
| `4` | Pendente de Regularização |
| `5` | Cancelada multiplicidade |
| `8` | Nula |
| `9` | Cancelada de ofício |
| qualquer outro | Situação desconhecida |

Nota: os códigos `6` e `7` não estão mapeados. Se o serviço retornar esses valores, o resultado será `"Situação desconhecida"`.

---

## 10. Estado atual do projeto

### O que já está implementado

- [x] Servidor HTTP Spring Boot funcional na porta 8081
- [x] Endpoint REST `GET /cpf/{cpf}` que aceita consultas
- [x] Interface web básica (HTML/CSS/JS) acessível na raiz
- [x] Construção manual de envelope SOAP 1.2
- [x] Serialização de objetos Java para XML via JAXB (`XmlParser.toXml`)
- [x] Chamada HTTP POST ao serviço externo via `RestTemplate` + Apache HttpClient 5
- [x] Desserialização da resposta XML para objetos Java (`XmlParser.fromXml`)
- [x] Validações de nulidade em cascata na resposta
- [x] Tradução dos códigos numéricos da RFB para descrições legíveis
- [x] Tratamento básico de erros com `RuntimeException`

---

### O que parece faltar

**Funcional:**
- [ ] A resposta ao usuário é apenas a situação cadastral em texto puro (`"Regular"`). Nenhuma outra informação é retornada (nome, CPF formatado, data de nascimento), mesmo estando disponíveis no objeto `DadosCPF`.
- [ ] Não há validação do número de CPF antes de enviar ao serviço (dígitos verificadores, tamanho, caracteres inválidos).
- [ ] Não há formatação do CPF de entrada. O usuário pode digitar `123.456.789-00` ou `12345678900` — o sistema não normaliza.

**Técnico:**
- [ ] Ausência de um DTO de resposta — o controller retorna `String` diretamente, o que torna difícil adicionar mais campos no futuro e não segue o padrão REST de retornar JSON estruturado.
- [ ] O `RestTemplate` e o `HttpClient` são criados a cada requisição (dentro do método `consultarCpf`). O correto seria declará-los como beans Spring (componentes reutilizáveis) para evitar criação desnecessária de objetos.
- [ ] O `JAXBContext` em `XmlParser` também é criado a cada chamada — operação custosa que deveria ser cacheada.
- [ ] O `numeroProtocolo` (`"RJP1234567890"`) está hardcoded. Em produção, cada requisição provavelmente precisaria de um protocolo único e rastreável.
- [ ] Não há um mecanismo de autenticação ou autorização na API REST (qualquer pessoa na rede pode chamar o endpoint).
- [ ] Ausência de tratamento de erros estruturado — seria adequado um `@ControllerAdvice` com `@ExceptionHandler` para retornar respostas de erro padronizadas.
- [ ] Uso de `System.out.println` para debug — deveria ser substituído por um framework de logging como SLF4J/Logback (já incluso no Spring Boot).

**Frontend:**
- [ ] O CSS é referenciado incorretamente em `index.html`: `href="css/style.css"`, mas o arquivo está em `static/style.css` (o caminho correto seria `href="style.css"`). O estilo não é aplicado.
- [ ] Não há feedback visual durante a chamada (ex.: spinner de carregamento).
- [ ] Não há tratamento de erro no JavaScript: se a API retornar status 500, o usuário vê o texto bruto do erro.

**Testes:**
- [ ] Nenhum teste de unidade para `CpfService`, `CpfSoapClient` ou `XmlParser`.
- [ ] Nenhum teste de integração do endpoint REST.
- [ ] O único teste existente (`contextLoads`) é gerado automaticamente pelo Spring Initializr e não testa funcionalidade alguma.

---

### Possíveis pontos de melhoria

1. **Retornar JSON estruturado** — criar um record/DTO como:
   ```java
   record CpfResponse(String cpf, String nome, String situacao, String dataNascimento) {}
   ```
   e retornar isso como `ResponseEntity<CpfResponse>` no controller.

2. **Externalizar configurações** — mover a URL do serviço SOAP, o `codServico`, `versao`, `codEvento` e `numeroProtocolo` para o `application.yaml`, tornando-os configuráveis por ambiente (homologação vs. produção) sem recompilar.

3. **Beans gerenciados pelo Spring** — `RestTemplate` e `HttpClient` deveriam ser `@Bean` em uma classe `@Configuration`, injetados pelo Spring em vez de instanciados manualmente.

4. **Tratamento de erros centralizado** — um `@RestControllerAdvice` que intercepte `RuntimeException` e retorne respostas padronizadas.

5. **Logging adequado** — substituir `System.out.println` por `private static final Logger log = LoggerFactory.getLogger(CpfService.class)` com `log.info(...)`.

6. **Cache de JAXBContext** — `JAXBContext.newInstance()` é uma operação lenta; deveria ser armazenado como campo estático ou bean.

---

### Dúvidas e observações encontradas durante a análise

1. **Por que `SoapBody` tem tanto `consultaCPFRequest` quanto `consultaCPFResponse`?**  
   A mesma classe `SoapBody` é usada para serializar a saída (request) e desserializar a entrada (response). Funciona, mas é uma ambiguidade. Uma separação em `SoapRequestBody` e `SoapResponseBody` tornaria o código mais claro.

2. **O serviço retorna `List<DadosCPF>` — em que situação viria mais de um resultado?**  
   O contrato do SERPRO admite a possibilidade, mas na prática CPF é um identificador único. Pode ser um remanescente do contrato genérico do web service (que talvez permita consulta em lote), mas o código atual só usa `get(0)`.

3. **O campo `dataNascimento` de `DadosCPF` nunca é usado**  
   O dado é desserializado mas nunca retornado ao usuário ou logado. Provavelmente está mapeado para uso futuro.

4. **O `numeroProtocolo` hardcoded `"RJP1234567890"` parece um valor de teste**  
   Em produção, espera-se que este protocolo seja gerado dinamicamente ou configurado por ambiente. Deixá-lo fixo pode causar problemas de rastreabilidade ou rejeição pelo serviço SOAP.

5. **Versão do Spring Boot: 4.0.6**  
   Esta é uma versão bastante recente (2025). O `spring-boot-starter-webmvc-test` e `spring-boot-starter-webservices-test` são artefatos novos nesta versão (anteriormente eram parte de `spring-boot-starter-test`). Vale atenção ao atualizar ou buscar tutoriais, pois a maioria da documentação online ainda cobre Spring Boot 3.x.

6. **Ausência de HTTPS**  
   A URL do serviço externo usa `http://` (sem TLS). CPF é dado pessoal sensível — em produção, a comunicação deveria ser sobre HTTPS.

7. **Ausência de `<soap:Header>`**  
   O envelope SOAP montado não possui `<soap:Header>`. Dependendo do ambiente de produção, autenticação ou tokens de acesso podem precisar ser enviados no header SOAP.
