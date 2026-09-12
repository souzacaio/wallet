# Decisões Técnicas

Este documento registra as principais escolhas de design, por que foram feitas e o que poderia ser
melhorado com mais tempo.

## 1. Concorrência: lock pessimista na linha da carteira

**Escolha:** cada crédito/débito roda dentro de uma única transação que primeiro adquire um lock
`SELECT ... FOR UPDATE` na linha da carteira alvo (`@Lock(PESSIMISTIC_WRITE)`), depois lê o registro
de idempotência, aplica a operação e faz commit.

**Por quê:**
- O requisito inegociável é *correção acima de tudo* — o saldo nunca pode ficar negativo e nenhuma
  operação pode ser aplicada duas vezes, mesmo sob tráfego concorrente intenso na mesma carteira.
- Um lock pessimista **serializa** todas as operações de uma carteira. Uma vez que a requisição
  detém o lock, ela lê o saldo commitado mais recente, verifica a invariante e escreve — sem chance
  de lost update ou de uma corrida check-then-act.
- O lock é **por carteira**, então operações em carteiras *diferentes* continuam rodando totalmente
  em paralelo; o ponto de contenção fica naturalmente restrito a uma única conta.
- Adquirir o lock **antes** da busca de idempotência faz com que a busca rode contra o estado
  commitado (isolamento padrão `READ COMMITTED` do PostgreSQL), o que fecha a corrida de replay:
  chamadas concorrentes com a mesma chave são serializadas e as posteriores enxergam a transação já
  commitada pela primeira.

**Defesa em profundidade (constraints do banco):**
- `CHECK (balance >= 0)` em `wallets` — a invariante se mantém mesmo que o código da aplicação seja contornado.
- `UNIQUE (wallet_id, idempotency_key)` em `transactions` — torna a dupla aplicação fisicamente
  impossível; uma corrida que passasse pela checagem da aplicação falharia atomicamente no banco.
- `CHECK (amount > 0)` em `transactions`.

**Alternativas consideradas:**
- **Lock otimista (`@Version`)**: mais leve sob baixa contenção, mas exige um loop de retentativa em
  conflitos de versão. Numa carteira "quente" com muitas escritas concorrentes, isso degenera em
  tempestades de retry. A coluna `version` ainda está presente como guarda extra, mas o lock
  pessimista é o mecanismo principal porque dá comportamento previsível sob contenção.
- **Isolamento serializable**: correto, mas empurra o tratamento de conflito para retentativas por
  falha de serialização em toda a transação — mais partes móveis do que um lock de linha direcionado.
- **`UPDATE ... SET balance = balance - :amount WHERE balance >= :amount` atômico**: elegante para
  débitos, mas a inserção no extrato + o tratamento de idempotência ainda precisam de coordenação,
  então o lock explícito mantém o fluxo uniforme tanto para créditos quanto para débitos.

## 2. Idempotência via header `Idempotency-Key`

**Escolha:** os clientes enviam um header `Idempotency-Key` por operação. A chave é armazenada no
registro do extrato com uma constraint `UNIQUE (wallet_id, idempotency_key)`.

- Mesma chave + mesmo payload → a transação original é retornada (retry seguro).
- Mesma chave + payload diferente (tipo ou valor) → `409 Conflict`, porque reusar uma chave para uma
  operação diferente é erro do cliente, não uma retentativa.

**Por que um header, e não um campo no body:** idempotência é uma preocupação de nível de transporte
(retries, timeouts); mantê-la fora do payload de negócio espelha a prática comum de mercado (ex.:
Stripe) e mantém o corpo da requisição focado na operação em si.

## 3. Dinheiro como `BigDecimal` / `NUMERIC(19,2)`

Valores em BRL usam `BigDecimal` no Java e `NUMERIC(19,2)` no PostgreSQL — aritmética decimal exata,
nunca ponto flutuante. As duas casas decimais são garantidas na borda da API (`@Digits`) e no schema.

## 4. Semântica dos status HTTP

- **400** — a requisição é sintática/estruturalmente inválida: JSON malformado, header obrigatório
  ausente, `amount <= 0`, parâmetros de paginação inválidos. A requisição em si está errada.
- **401** — API key ausente/inválida.
- **404** — a carteira informada no path não existe.
- **409** — a requisição está bem-formada mas conflita com o estado existente: uma chave de
  idempotência já usada para uma operação *diferente*.
- **422** — a requisição está bem-formada e compreendida, mas viola uma regra de negócio que não é
  uma simples validação de campo: um débito válido que deixaria o saldo negativo. Usar 422 (em vez de
  400) diferencia "saldo insuficiente" de "sua requisição está malformada", o que importa para o
  consumidor da API decidir se uma retentativa poderia algum dia dar certo.

## 5. Migrations com Flyway

O schema é versionado em `src/main/resources/db/migration` (`V1`, `V2`). O `hibernate.ddl-auto` está
configurado como `validate` — o Hibernate nunca altera o schema; apenas verifica se as entidades
batem com o schema migrado. Isso mantém as mudanças de schema explícitas, revisáveis e reproduzíveis.

## 6. Extrato de transações imutável

As transações são append-only e armazenam `balance_after`. O `balance` da carteira é um total
acumulado materializado, mantido consistente dentro da mesma transação travada. Isso dá leitura de
saldo em O(1) preservando um histórico completo e auditável.

## 7. Camadas

`web` (controllers/DTOs) → `service` (regras de negócio + fronteiras de transação) → `repository`
(persistência) → `domain` (entidades donas de suas invariantes, ex.: `Wallet.debit` rejeita saques a
descoberto). As invariantes de domínio vivem na entidade, não espalhadas pelos services.

---

## O que eu faria diferente com mais tempo

- **Correlação/observabilidade**: logging estruturado com um id de requisição/correlação, além de
  métricas (contagem de operações, taxa de rejeição, tempo de espera do lock) e tracing.
- **Pipeline de CI** rodando a suíte de testes e construindo a imagem a cada push.
- **API Gateway**: utilização de API Gateway como camada de entrada das APIs, centralizando roteamento, segurança, autenticação, controle de acesso e gerenciamento das requisições entre clientes e serviços.