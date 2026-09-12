# Serviço de Carteira Digital

Serviço REST de backend para uma carteira digital em BRL. As carteiras guardam saldo em Reais e
aceitam operações de **crédito** (entrada de dinheiro) e **débito** (saída). O serviço foi feito
para rodar sob tráfego concorrente real, garantindo duas invariantes inegociáveis:

1. **O saldo nunca pode ficar negativo.**
2. **Nenhuma operação é aplicada duas vezes** (gravações idempotentes), mesmo sob retentativas ou chamadas concorrentes.

## Tecnologias

- Java 17, Spring Boot 3.3
- Spring Web, Spring Data JPA, Bean Validation
- PostgreSQL 16 (em container) + migrations Flyway
- springdoc-openapi (Swagger UI)
- JUnit 5, Mockito, Testcontainers

---

## Executando o projeto

### Pré-requisitos

- Docker + Docker Compose
- (Para rodar localmente sem containers) JDK 17 e Maven 3.9+

### Opção A — tudo no Docker (recomendado)

Constrói a imagem da aplicação e sobe o PostgreSQL + a API:

```bash
docker compose up --build
```

A API fica disponível em `http://localhost:8080` e o Swagger UI em
`http://localhost:8080/swagger-ui.html`.

Parar e limpar tudo (incluindo o volume do banco):

```bash
docker compose down -v
```

### Opção B — banco no Docker, aplicação via Maven

Suba apenas o banco:

```bash
docker compose up -d db
```

Rode a aplicação:

```bash
mvn spring-boot:run
```

A aplicação lê a configuração a partir de variáveis de ambiente (valores padrão entre parênteses):

| Variável         | Padrão                                          |
|------------------|-------------------------------------------------|
| `DB_URL`         | `jdbc:postgresql://localhost:5432/wallet`       |
| `DB_USERNAME`    | `wallet`                                         |
| `DB_PASSWORD`    | `wallet`                                          |
| `WALLET_API_KEY` | `local-dev-api-key`                              |
| `SERVER_PORT`    | `8080`                                           |

---

## Autenticação

Todos os endpoints (exceto o Swagger e a documentação OpenAPI) exigem uma API key enviada no
header `X-API-Key`:

```
X-API-Key: local-dev-api-key
```

Requisições sem uma chave válida recebem `401 Unauthorized`.

---

## API

URL base: `http://localhost:8080`

### Criar uma carteira

```bash
curl -X POST http://localhost:8080/wallets \
  -H "X-API-Key: local-dev-api-key" \
  -H "Content-Type: application/json" \
  -d '{"holderName":"Alice"}'
```

`201 Created`
```json
{
  "id": "b1f2...",
  "holderName": "Alice",
  "balance": 0.00,
  "createdAt": "2026-09-12T12:00:00Z",
  "updatedAt": "2026-09-12T12:00:00Z"
}
```

### Consultar uma carteira / saldo

```bash
curl http://localhost:8080/wallets/{id} -H "X-API-Key: local-dev-api-key"
```

### Registrar uma operação (crédito / débito)

O header `Idempotency-Key` identifica unicamente a operação. Repetir a mesma chave retorna
o resultado original sem aplicá-la de novo.

```bash
curl -X POST http://localhost:8080/wallets/{id}/transactions \
  -H "X-API-Key: local-dev-api-key" \
  -H "Idempotency-Key: 5f1c9e2a-..." \
  -H "Content-Type: application/json" \
  -d '{"type":"CREDIT","amount":100.00}'
```

`201 Created`
```json
{
  "message": "Transação criada com sucesso.",
  "replayed": false,
  "transaction": {
    "id": "c3a7...",
    "walletId": "b1f2...",
    "type": "CREDIT",
    "amount": 100.00,
    "balanceAfter": 100.00,
    "idempotencyKey": "5f1c9e2a-...",
    "createdAt": "2026-09-12T12:01:00Z"
  }
}
```

Ao reenviar a **mesma** `Idempotency-Key` com o mesmo payload, a resposta é `200 OK` com
`"replayed": true` e uma mensagem informando que a operação já havia sido processada — o saldo
**não** é alterado novamente.

### Extrato (paginado + filtro por período)

```bash
curl "http://localhost:8080/wallets/{id}/transactions?page=0&size=20&from=2026-09-01T00:00:00Z&to=2026-09-30T23:59:59Z" \
  -H "X-API-Key: local-dev-api-key"
```

Parâmetros de query: `page` (padrão 0), `size` (padrão 20, máx. 100), `from` / `to` (instantes
ISO-8601, opcionais). Os resultados são ordenados do mais recente para o mais antigo.

### Formato de erro

Todos os erros compartilham um payload padronizado:

```json
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "Debit of 100.01 would make wallet ... balance negative (current: 100.00)",
  "status": 422,
  "timestamp": "2026-09-12T12:02:00Z",
  "path": "/wallets/.../transactions",
  "errors": null
}
```

| Situação                                                  | Status | Código                 |
|-----------------------------------------------------------|--------|------------------------|
| Body/params inválidos, valor ≤ 0, JSON malformado         | 400    | `VALIDATION_ERROR` / `MALFORMED_REQUEST` |
| Header `Idempotency-Key` ausente                          | 400    | `MISSING_HEADER`       |
| API key ausente / inválida                                | 401    | `UNAUTHORIZED`         |
| Carteira não existe                                       | 404    | `WALLET_NOT_FOUND`     |
| Mesma chave de idempotência reusada com payload diferente | 409    | `IDEMPOTENCY_CONFLICT` |
| Débito deixaria o saldo negativo                          | 422    | `INSUFFICIENT_BALANCE` |

As escolhas de status são explicadas em [DECISIONS.md](DECISIONS.md).

---

## Modelo de dados

**wallet**
- `id` (UUID, gerado pelo serviço)
- `holder_name`
- `balance` `NUMERIC(19,2)`, começa em `0.00`, `CHECK (balance >= 0)` no banco
- `version` (coluna de lock otimista, defesa em profundidade)
- `created_at`, `updated_at`

**transaction** (registro imutável do extrato)
- `id` (UUID)
- `wallet_id` (FK)
- `type` (`CREDIT` / `DEBIT`)
- `amount` `NUMERIC(19,2)`, `CHECK (amount > 0)` no banco
- `balance_after` — saldo da carteira logo após este registro ser aplicado
- `idempotency_key` — `UNIQUE (wallet_id, idempotency_key)`
- `created_at`

O saldo de uma carteira é o total acumulado das transações aplicadas; cada transação registra
`balance_after`, tornando o extrato totalmente auditável.

---

## Estratégia de concorrência

A correção sob concorrência é garantida com um **lock pessimista de escrita** na linha da carteira,
apoiado por constraints do banco como defesa em profundidade. Veja o [DECISIONS.md](DECISIONS.md)
para a justificativa completa e as alternativas consideradas.

Para uma operação recebida, o serviço, dentro de uma única transação:

1. Trava a linha da carteira (`SELECT ... FOR UPDATE`), serializando todas as operações daquela carteira.
2. Busca o par `(wallet_id, idempotency_key)`:
   - encontrado com o **mesmo** payload → retorna o resultado original (replay idempotente),
   - encontrado com payload **diferente** → `409 Conflict`,
   - não encontrado → prossegue.
3. Aplica o crédito/débito e valida a invariante (`balance >= 0`).
4. Persiste o registro no extrato e o saldo atualizado, então faz commit (liberando o lock).

Redes de segurança no nível do banco: `CHECK (balance >= 0)` e a constraint única de idempotência
garantem as invariantes mesmo que a lógica da aplicação seja contornada.

---

## Testes

Rode a suíte completa (unitários + integração). Os testes de integração sobem um PostgreSQL real
via Testcontainers, então **o Docker precisa estar rodando**:

```bash
mvn test
```

O que é coberto:

- **Unitários** (`WalletTest`, `TransactionServiceTest`) — aritmética de crédito/débito, rejeição
  por saldo insuficiente, replay idempotente, conflito por reuso de chave, carteira inexistente.
- **Integração** (`WalletApiIntegrationTest`) — fluxo HTTP ponta a ponta, semântica dos status
  HTTP, aplicação da API key, comportamento de idempotência e conflito.
- **Concorrência** (`ConcurrencyIntegrationTest`) — 50 débitos paralelos contra um saldo que só
  cobre 10 (exatamente 10 têm sucesso, o saldo termina em `0.00`, nunca negativo) e 30 replays
  concorrentes da mesma chave (aplicada exatamente uma vez).

---

## Estrutura do projeto

```
src/main/java/br/com/datum/wallet
├── config/       ApiKeyFilter, OpenApiConfig
├── domain/       Wallet, Transaction, TransactionType, exceções
├── repository/   WalletRepository (lock pessimista), TransactionRepository
├── service/      WalletService, TransactionService (lógica central de concorrência)
└── web/          WalletController, DTOs, tratamento de erros
src/main/resources/db/migration   Migrations Flyway (V1, V2)
```

## Decisões técnicas

Veja o [DECISIONS.md](DECISIONS.md).
