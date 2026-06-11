# SensorHub - Guia para Agentes

## Contexto do produto

O SensorHub é um monorepo para desenvolver uma solução de monitoramento de ambientes com sensores IoT.

O produto principal será um aplicativo mobile em Flutter. O usuário poderá cadastrar dispositivos usando um UUID, associar cada dispositivo a um ambiente e visualizar dados como temperatura, umidade, última atualização e histórico de medições.

Os dados serão enviados por dispositivos físicos, como Raspberry Pi ou microcontroladores, via MQTT. O papel do MQTT no projeto é transportar os dados brutos dos sensores até uma etapa de ingestão, funcionando como parte de um fluxo parecido com ETL: o sensor publica a leitura, um ingestor consome a mensagem, valida e transforma os dados quando necessário, e persiste as medições no PostgreSQL.

Na primeira fase, principalmente para acelerar o desenvolvimento, esse fluxo poderá ser simulado. Em vez de depender de sensor físico real, o monorepo pode conter um script Python responsável por gerar dados simulados e publicá-los como se eles tivessem passado pelo fluxo sensor -> MQTT -> ingestor.

## Objetivo do monorepo

Manter, em um único repositório, as aplicações e os pacotes que compõem o SensorHub:

- Aplicativo mobile Flutter.
- API/backend em Java 25 com Spring Boot, JPA e Flyway para consulta e gerenciamento.
- Ingestor em Java puro, com uso mínimo de frameworks, para ingestão de mensagens MQTT e persistência em PostgreSQL.
- Script Python de simulação para gerar dados mockados na primeira fase e publicar medições via MQTT.
- Pacotes compartilhados de contratos, modelos, validações ou documentação.
- Especificações funcionais e técnicas que orientam o desenvolvimento por agentes.

## Estrutura esperada

A estrutura inicial deve evoluir de forma incremental. Use estes diretórios como referência:

```text
SensorHub/
  AGENTS.md
  docker-compose.yml
  specs/
  apps/
    mobile/
    api/
    ingestor/
    sensor/
  packages/
    shared/
  infra/
  docs/
```

Crie diretórios apenas quando forem necessários para uma entrega concreta. Evite scaffolds vazios demais.

## Regras de trabalho

- Antes de implementar, leia as specs relevantes em `specs/`.
- Se a tarefa alterar o comportamento do produto, atualize ou crie uma spec antes ou junto da implementação.
- Preserve o estilo e as decisões já existentes no repositório.
- Prefira mudanças pequenas, verificáveis e bem delimitadas.
- Não misture refatorações amplas com implementação de funcionalidade.
- Quando houver ambiguidade, registre a decisão tomada na spec ou em um arquivo de decisão técnica.
- Use nomes em inglês para código, diretórios, APIs e identificadores.
- Textos voltados ao usuário final podem ser em português.
- Mantenha dados sensíveis fora do repositório. Use arquivos de exemplo para variáveis de ambiente.
- Toda aplicação criada deve ter execução prevista dentro de um `docker-compose.yml`.
- Ao adicionar uma aplicação, inclua ou atualize sua configuração de container, variáveis de ambiente e dependências no Docker Compose.
- O ambiente local deve permitir subir os serviços necessários com Docker Compose, incluindo PostgreSQL e as aplicações já implementadas.

## Fluxo recomendado por specs

Cada nova funcionalidade deve partir de uma especificação curta contendo:

- Problema ou objetivo.
- Usuários ou sistemas envolvidos.
- Comportamento esperado.
- Dados de entrada e saída.
- Regras de negócio.
- Estados de erro.
- Critérios de aceite.
- Impacto técnico previsto.

Specs devem ser práticas. Elas precisam orientar implementação e testes, não servir como documentação extensa sem uso.

Antes de criar aplicações, comece pelas estruturas de dados e contratos:

- JSON produzido pelos sensores.
- Domínios principais do sistema.
- Atributos de cada domínio.
- Relacionamentos que darão origem às tabelas.
- Contratos de persistência e consulta que a API deverá respeitar.

Essas definições devem guiar o script Python, a API, o app mobile e, posteriormente, o ingestor MQTT e o firmware.

## Domínios principais

### Dispositivos

Um dispositivo representa um sensor físico cadastrado pelo usuário. Deve possuir pelo menos:

- UUID único.
- Nome amigável opcional.
- Ambiente associado.
- Status operacional inferido a partir da última leitura.
- Data da última comunicação.

### Ambientes

Um ambiente representa o local monitorado, como quarto, sala, escritório ou laboratório.

O sistema deve permitir que o usuário organize dispositivos por ambiente e visualize leituras agregadas ou individuais.

### Medições

Uma medição representa uma leitura enviada pelo dispositivo. Inicialmente, deve contemplar:

- Temperatura.
- Umidade.
- Timestamp da leitura.
- UUID do dispositivo.

### Ingestão MQTT

Dispositivos físicos publicarão mensagens MQTT. A aplicação intermediária deve tratar essa ingestão como um fluxo de coleta, validação, normalização e persistência de dados de sensores.

- Assinar tópicos definidos para telemetria.
- Validar formato e UUID do dispositivo.
- Normalizar dados recebidos.
- Persistir medições válidas.
- Registrar falhas relevantes para diagnóstico.

O objetivo dessa etapa não é atender diretamente o app mobile. O objetivo é mover dados do sensor para o banco de dados de forma confiável. A API ficará responsável por consultar os dados persistidos e entregá-los ao app.

### Simulação de sensores

Na primeira fase do projeto, a ingestão real via MQTT pode ser substituída por um script Python de simulação.

Esse script deve:

- Gerar leituras simuladas de temperatura e umidade.
- Usar UUIDs de dispositivos conhecidos ou configuráveis.
- Simular timestamps de medição.
- Publicar os dados via MQTT.
- Permitir desenvolvimento do app e da API sem depender de hardware físico, broker MQTT ou ingestor real.

Quando o fluxo real for implementado, o script deve continuar útil para testes locais, demos e geração de massa de dados.

### API

A API deve ser implementada em Java 25 com Spring Boot, JPA e Flyway. Ela deve permitir que o aplicativo mobile:

- Cadastre dispositivos por UUID.
- Liste ambientes.
- Associe dispositivos a ambientes.
- Consulte leituras atuais.
- Consulte histórico de medições.

### Aplicativo mobile

O app Flutter deve priorizar uma experiência simples para:

- Cadastrar um dispositivo.
- Nomear e associar o dispositivo a um ambiente.
- Ver a leitura atual por ambiente.
- Ver histórico de temperatura e umidade.
- Identificar quando um dispositivo está sem atualização recente.

## Decisões técnicas

Estas decisões já estão definidas para orientar o desenvolvimento inicial:

- Aplicativo mobile: Flutter.
- API/backend: Java 25, Spring Boot, JPA e Flyway.
- Ingestor MQTT: Java puro, com uso mínimo de frameworks.
- Banco de dados: PostgreSQL.
- Script de simulação de sensores: Python.
- Execução local e integração entre serviços: Docker Compose.

O ingestor deve priorizar simplicidade, baixo overhead e eficiência. Evite frameworks pesados nessa aplicação, a menos que a necessidade técnica seja clara e documentada.

Estas decisões ainda estão abertas e devem ser fechadas conforme as specs evoluírem:

- Broker MQTT.
- Estratégia de autenticação.
- Estratégia de deploy local e em produção.

Ao escolher qualquer uma delas, documente a motivação em `specs/` ou `docs/`.

## Qualidade e verificação

Para cada entrega, busque incluir pelo menos uma forma objetiva de verificação:

- Testes automatizados quando houver lógica de domínio.
- Testes de widget ou integração para fluxos importantes no Flutter.
- Validação de contrato para payloads MQTT e API.
- Comandos de build, lint ou formatação quando a stack já estiver definida.

Quando não for possível testar, explique claramente o motivo e o risco restante.

## Backlog inicial sugerido

1. Definir a arquitetura inicial do monorepo e do Docker Compose.
2. Especificar o JSON produzido pelos sensores, mesmo que inicialmente seja usado apenas pelo simulador.
3. Especificar os domínios principais: dispositivo, ambiente e medição.
4. Especificar atributos, tabelas, relacionamentos e regras de integridade para PostgreSQL.
5. Incluir PostgreSQL no Docker Compose.
6. Criar scaffold da API em Java 25 com Spring Boot, JPA e Flyway.
7. Implementar migrations iniciais com Flyway dentro da API.
8. Implementar endpoints iniciais para cadastro de dispositivo, ambientes, leitura atual e histórico.
9. Incluir a API no Docker Compose.
10. Definir a estratégia de dados mockados para a primeira fase.
11. Criar o script Python para simular sensores e publicar medições via MQTT.
12. Incluir o script Python no Docker Compose.
13. Criar scaffold do app Flutter.
14. Implementar fluxos do app para cadastro de dispositivo, associação a ambiente, leitura atual e histórico.
15. Incluir o app mobile no fluxo de desenvolvimento previsto pelo Docker Compose quando aplicável.
16. Em fase posterior, especificar e implementar o firmware ou integração com sensor físico.
17. Em fase posterior, especificar e implementar o ingestor MQTT em Java puro.
