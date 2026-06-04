# Specs do SensorHub

Esta pasta guarda as especificações que orientam o desenvolvimento do SensorHub.

Use specs para transformar ideias em trabalho implementável. Cada spec deve ser curta, objetiva e verificável.

## Specs atuais

- `001-product-overview.md`: visão geral do produto e escopo inicial.
- `002-api.md`: domínio, persistência, migrations e contratos iniciais da API.
- `003-mock-sensor.md`: script Python para gerar medições simuladas no PostgreSQL.

## Template

```markdown
# Título da spec

## Objetivo

Descreva o problema ou resultado esperado.

## Escopo

Liste o que está incluído e o que está fora por enquanto.

## Comportamento esperado

Descreva os fluxos principais.

## Dados e contratos

Defina entradas, saídas, payloads, modelos ou estados relevantes.

## Regras de negócio

Liste regras que a implementação deve respeitar.

## Erros e casos limite

Liste falhas esperadas e como devem ser tratadas.

## Critérios de aceite

Liste condições objetivas para considerar a entrega pronta.

## Observações técnicas

Registre decisões, dependências ou pontos ainda em aberto.
```
