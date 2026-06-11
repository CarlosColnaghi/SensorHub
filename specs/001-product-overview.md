# Visão geral do produto

## Objetivo

Desenvolver o SensorHub, uma solução para monitoramento de ambientes usando sensores IoT.

O sistema deve permitir que usuários cadastrem dispositivos por UUID, associem esses dispositivos a ambientes e acompanhem temperatura, umidade, última atualização e histórico de medições pelo aplicativo mobile.

O projeto será desenvolvido em fases. A primeira fase deve priorizar a definição dos contratos de dados, o modelo de domínio, a persistência em PostgreSQL, um simulador de sensores em Python, uma API em Java 25 com Spring Boot e um app mobile em Flutter.

## Escopo da primeira fase

Incluído na primeira fase:

- Especificação do JSON produzido pelos sensores.
- Especificação dos domínios principais, atributos, tabelas e relacionamentos.
- Banco de dados PostgreSQL.
- Broker MQTT para desenvolvimento local.
- Script Python para simular sensores e publicar medições via MQTT.
- Ingestor MQTT em Java 25 para consumir telemetria e persistir medições no PostgreSQL.
- API em Java 25 com Spring Boot, JPA e Flyway.
- Aplicativo Flutter para cadastrar dispositivos, associar ambientes e visualizar medições.
- Docker Compose para executar os serviços criados e suas dependências.

Fora da primeira fase:

- Firmware ou integração com sensor físico real.
- Alertas push.
- Controle remoto de dispositivos.
- Automações.
- Multiusuário avançado.
- Dashboard web.
- Provisionamento automático de hardware.

## Stack definida

- Aplicativo mobile: Flutter.
- API/backend: Java 25, Spring Boot, JPA e Flyway.
- Banco de dados: PostgreSQL.
- Broker MQTT local: Eclipse Mosquitto.
- Simulador de sensores: Python.
- Ingestor MQTT: Java 25, com uso mínimo de frameworks.
- Execução local e integração entre serviços: Docker Compose.

## Usuários e sistemas envolvidos

- Usuário final: cadastra dispositivos, organiza ambientes e consulta medições.
- Simulador Python: gera leituras mockadas de sensores e publica mensagens MQTT.
- Broker MQTT: transporta mensagens de telemetria entre simulador ou sensores físicos e o ingestor.
- API: expõe dados e operações para o aplicativo mobile.
- Banco de dados: armazena ambientes, dispositivos e medições.
- App mobile: interface principal do usuário.
- Dispositivo físico: publicará leituras de sensores via MQTT em fase futura.
- Ingestor MQTT: consome mensagens MQTT, valida payloads e persiste medições.

## Fluxo da primeira fase

1. A estrutura de dados dos sensores é especificada em JSON.
2. Os domínios, atributos, tabelas e relacionamentos são definidos.
3. O PostgreSQL é configurado no Docker Compose.
4. O broker MQTT é configurado no Docker Compose.
5. O script Python gera leituras simuladas de temperatura e umidade.
6. O script Python publica as leituras no broker MQTT.
7. O ingestor MQTT consome as mensagens e persiste as medições no PostgreSQL.
8. A API consulta e gerencia os dados persistidos.
9. O app Flutter consome a API para exibir leitura atual e histórico.

## Fluxo de ingestão com MQTT

1. O simulador Python ou um dispositivo físico publica uma leitura MQTT contendo seu UUID, temperatura, umidade e timestamp.
2. O ingestor MQTT em Java 25 consome a mensagem.
3. O ingestor valida o payload, identifica o dispositivo e normaliza os dados quando necessário.
4. Leituras válidas são armazenadas no PostgreSQL.
5. A API consulta os dados persistidos.
6. O app Flutter exibe leitura atual, última atualização e histórico.

## Contrato inicial do sensor

O JSON abaixo representa o formato inicial esperado para leituras de sensores publicadas via MQTT. Na primeira fase, ele será produzido pelo simulador Python. Em fase futura, o mesmo contrato servirá como base para sensores físicos.

```json
{
  "hardwareUuid": "2f4a7d8e-3b6a-4a5c-9f2b-8f4d0d8f3c21",
  "temperature": 24.7,
  "temperatureUnit": "CELSIUS",
  "humidity": 58.2,
  "humidityUnit": "RELATIVE_PERCENT",
  "measuredAt": "2026-06-01T14:30:00Z"
}
```

Campos:

- `hardwareUuid`: UUID físico do dispositivo que gerou a leitura, definido pelo firmware ou simulador.
- `temperature`: temperatura medida pelo sensor.
- `temperatureUnit`: unidade da temperatura medida, inicialmente `CELSIUS`.
- `humidity`: umidade medida pelo sensor.
- `humidityUnit`: unidade da umidade medida, inicialmente `RELATIVE_PERCENT`.
- `measuredAt`: timestamp da medição informado pelo sensor ou simulador.

O campo `receivedAt` não deve vir do sensor. Ele deve ser definido pelo sistema no momento em que a medição for persistida.

Os campos `temperatureUnit` e `humidityUnit` devem vir do sensor ou simulador no contrato inicial. O sistema deve persistir a unidade recebida junto do valor medido. Conversões de unidade podem ser adicionadas posteriormente, sem alterar o significado original da medição armazenada.

## Entidades iniciais

### User

Representa o usuário dono dos dispositivos cadastrados.

Campos esperados:

- `uuid`
- `name`
- `email`
- `createdAt`
- `updatedAt`

Relacionamentos:

- Um usuário pode possuir vários ambientes.
- Um usuário pode possuir vários dispositivos.
- Um dispositivo deve estar associado a um usuário.

### Device

Representa o sensor físico ou simulado.

Campos esperados:

- `uuid`
- `hardwareUuid`
- `userUuid`
- `name`
- `environmentUuid`
- `status`
- `createdAt`
- `updatedAt`

Relacionamentos:

- Um dispositivo deve estar associado a um usuário.
- Um dispositivo pode estar associado a no máximo um ambiente.
- Um dispositivo pode não estar associado a nenhum ambiente.
- Um dispositivo pode possuir várias medições.
- O `uuid` do dispositivo é o identificador interno do registro no sistema.
- O `hardwareUuid` é o identificador físico informado pelo firmware ou simulador.
- O `status` inicial do dispositivo deve ser `ACTIVATED`.
- A última comunicação do dispositivo deve ser derivada das medições persistidas, usando `measurements.receivedAt`, e não armazenada como atributo cadastral em `devices`.
- Status aceitos inicialmente: `ACTIVATED` e `INACTIVATED`.

### Environment

Representa um ambiente monitorado, como quarto, sala, escritório ou laboratório.

Campos esperados:

- `uuid`
- `userUuid`
- `name`
- `createdAt`
- `updatedAt`

Relacionamentos:

- Um ambiente deve estar associado a um usuário.
- Um ambiente pode possuir vários dispositivos.
- Um ambiente pode não possuir dispositivos.

### Measurement

Representa uma leitura recebida de um sensor físico ou simulado.

Campos esperados:

- `uuid`
- `deviceUuid`
- `temperature`
- `temperatureUnit`
- `humidity`
- `humidityUnit`
- `measuredAt`
- `receivedAt`

Relacionamentos:

- Uma medição pertence a um dispositivo.

## Regras iniciais

- UUID de usuário deve ser único.
- UUID de dispositivo deve ser único e gerado pelo banco como chave primária.
- `hardwareUuid` de dispositivo deve ser único e obrigatório.
- O cadastro de um dispositivo deve receber o `hardwareUuid` vindo do firmware ou simulador.
- O status de dispositivo deve aceitar inicialmente apenas `ACTIVATED` e `INACTIVATED`.
- Dispositivos criados sem status explícito devem iniciar como `ACTIVATED`.
- Um dispositivo deve pertencer a um usuário.
- Um ambiente deve pertencer a um usuário.
- Um dispositivo pode estar associado a no máximo um ambiente por vez.
- Associação de dispositivo a ambiente é opcional.
- Quando um dispositivo estiver associado a um ambiente, ambos devem pertencer ao mesmo usuário.
- Uma medição deve estar vinculada ao UUID interno de um dispositivo.
- Payloads de sensores devem identificar o dispositivo por `hardwareUuid`; o sistema deve resolver esse valor para o UUID interno do dispositivo antes de persistir a medição.
- Temperatura e umidade devem ser numéricas.
- Umidade deve representar percentual.
- Temperatura deve armazenar a unidade de medida informada pelo sensor, inicialmente `CELSIUS`.
- Umidade deve armazenar a unidade de medida informada pelo sensor, inicialmente `RELATIVE_PERCENT`.
- Conversões futuras devem preservar o valor e a unidade originais da medição recebida.
- O sistema deve diferenciar timestamp informado pelo sensor de timestamp de recebimento.
- Um dispositivo sem medições recentes deve poder ser exibido como offline.
- Toda aplicação criada deve ter execução prevista no Docker Compose.

## Critérios de aceite da primeira fase

- Existe uma especificação clara do JSON de leitura do sensor.
- Existem definições iniciais de domínios, atributos, tabelas e relacionamentos.
- PostgreSQL sobe via Docker Compose.
- Broker MQTT sobe via Docker Compose.
- Script Python gera medições simuladas e publica via MQTT.
- Ingestor MQTT consome mensagens e persiste medições no PostgreSQL.
- API Java 25/Spring Boot consulta os dados persistidos.
- API possui migrations iniciais com Flyway.
- App Flutter consegue cadastrar dispositivo por UUID.
- App Flutter consegue associar dispositivo a ambiente.
- App Flutter mostra leitura atual de temperatura e umidade.
- App Flutter mostra histórico básico de medições.
- App Flutter mostra quando foi a última atualização do dispositivo.

## Pontos em aberto

- Estratégia de autenticação.
- Estratégia de deploy em produção.
- Política para dispositivos que enviam dados antes de serem cadastrados pelo usuário.
- Detalhes de firmware ou hardware físico.
