# 🦜 Ararajuba Broker

> ⚠️ **Aviso:** Este projeto é um **MVP** (Minimum Viable Product). 
> Trata-se de um protótipo funcional de um broker de mensageria distribuída, desenvolvido 
> para o trabalho de conclusão da disciplina de Sistemas Distribuídos do curso de Sistemas de Informação - Instituto Federal Fluminense.

**Ararajuba Broker** é um sistema de **mensageria distribuída** desenvolvido em **Java 21** com **Quarkus 3.20**, inspirado em soluções como **Kafka** e **RabbitMQ**.  
O projeto implementa um **broker** próprio, com **controle de offsets**, **entrega confiável** e **múltiplos grupos de consumo**, permitindo publicação, consumo e confirmação (**ACK**) de mensagens.

---

## 🚀 Funcionalidades

- ✅ Publicação de mensagens em **tópicos**
- ✅ Suporte a **grupos de roteamento** (`routeGroup`) → cria subtópicos físicos dinâmicos
- ✅ Suporte a **grupos de consumo** (`consumerGroup`) → controle de offsets independentes
- ✅ Long polling para consumo eficiente
- ✅ Confirmação de mensagens com **ACK**
- ✅ Listagem de tópicos disponíveis
- ✅ Baseado em **Quarkus RESTEasy Reactive** + **WebSockets**
- ✅ Estrutura modular e extensível

---

## 🏗️ Arquitetura

```
┌─────────────┐      Publish      ┌──────────────┐
│  Producer   │  ──────────────▶  │ Ararajuba    │
│  (Client)   │                   │ Broker       │
└─────────────┘                   └──────────────┘
                                     │
                                     │ Poll
                                     ▼
                                ┌─────────────┐
                                │ Consumer(s) │
                                └─────────────┘
```

- **Producers** → Publicam mensagens em um **tópico**.
- **Broker** → Armazena mensagens, gerencia offsets e roteamento.
- **Consumers** → Consomem mensagens em grupos independentes.
- **Dispatcher** → Garante entrega ordenada e confiável.
- **CommitLog** → Persiste mensagens com offsets sequenciais.

---

## 🛠️ Tecnologias

| Tecnologia       | Versão  | Uso principal                    |
|------------------|---------|-----------------------------------|
| **Java**        | 21      | Linguagem principal             |
| **Quarkus**     | 3.20.x  | Framework para microservices    |
| **RESTEasy**    | —       | Endpoints REST                  |
| **Jackson**     | 2.x     | Serialização JSON               |
| **WebSockets**  | —       | Entrega assíncrona de mensagens |

---

## 📂 Estrutura do Projeto

```
ararajuba-broker/
├── src/
│   ├── main/java/br/iff/edu/ararajuba/
│   │   ├── api/            # Endpoints REST
│   │   ├── core/           # Serviços centrais (Dispatcher, Delivery)
│   │   ├── dto/            # DTOs e respostas
│   │   ├── log/            # CommitLog, RecordCodec e persistência
│   │   ├── service/        # BrokerService (regras de negócio)
│   │   └── state/          # Controle de offsets por consumerGroup
│   └── resources/          # application.properties e configs
├── pom.xml                 # Configuração do Maven + Quarkus
└── README.md               # Documentação do projeto
```

---

## ⚡ Endpoints REST

### **1. Publicar mensagem**
```http
POST /topics/{topic}/publish?group={routeGroup}
Content-Type: application/json
```
**Body:**
```json
{
  "key": "user-123",
  "value": "Minha primeira mensagem"
}
```
**Resposta:**
```json
{
  "offset": 0,
  "topic": "pedidos__lojaA"
}
```
> Se `group` não for informado, publica diretamente no tópico base.

---

### **2. Consumir mensagens (poll)**

```http
GET /topics/{topic}/poll?routeGroup={rg}&consumerGroup={cg}&max=10&timeoutMs=3000
```

**Parâmetros:**

| Nome            | Obrigatório | Descrição                               |
|-----------------|-------------|----------------------------------------|
| `routeGroup`    | Não         | Subtópico físico opcional             |
| `consumerGroup` | Não         | Grupo de consumo para commit de offsets |
| `max`           | Não         | Número máximo de mensagens           |
| `timeoutMs`     | Não         | Timeout para long-poll               |


**Resposta:**
```json
[
  {
    "offset": 0,
    "ts": 1699991112233,
    "key": "user-123",
    "value": "Minha primeira mensagem"
  }
]
```

---

### **3. Confirmar processamento (ACK)**

```http
POST /topics/{topic}/ack?routeGroup={rg}&consumerGroup={cg}
Content-Type: application/json
```

**Body:**
```json
{
  "ids": ["0", "1"]
}
```

**Resposta:**
```
204 No Content
```

---

### **4. Listar tópicos**

```http
GET /topics/{topic}/list
```
**Resposta:**
```json
[
  "pedidos",
  "pedidos__lojaA",
  "pagamentos"
]
```

---

## 🧩 Como Rodar o Projeto

### **1. Pré-requisitos**
- **Java 21**
- **Maven 3.9+**
- **Quarkus CLI** *(opcional, recomendado)*

### **2. Clonar o repositório**
```bash
git clone https://github.com/seu-usuario/ararajuba-broker.git
cd ararajuba-broker
```

### **3. Rodar em modo desenvolvimento**
```bash
./mvnw quarkus:dev
```
O serviço estará disponível em:
```
http://localhost:8080
```

### **4. Gerar build**
```bash
./mvnw clean package
```

---

## 🧪 Testando com cURL

**Publicar mensagem:**
```bash
curl -X POST http://localhost:8080/topics/test/publish   -H "Content-Type: application/json"   -d '{"key":"k1","value":"mensagem"}'
```

**Consumir mensagens:**
```bash
curl "http://localhost:8080/topics/test/poll?consumerGroup=app1&max=5"
```

---

## 📌 Próximos Passos

- [ ] Adicionar autenticação JWT
- [ ] Implementar replicação entre brokers
- [ ] Melhorar persistência com segmentação de logs
- [ ] Criar painel de monitoramento em tempo real

---

## 📝 Licença

Este projeto está licenciado sob a **MIT License** — veja o arquivo [LICENSE](LICENSE) para detalhes.

---

## ✍️ Autor

**Edivan Carvalho**  
📧 [email@example.com](mailto:email@example.com)  
🚀 Projeto acadêmico desenvolvido com **Quarkus** e **Java 21**.
