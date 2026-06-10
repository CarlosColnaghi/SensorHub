# Worker MQTT

## Objetivo

Definir o worker de ingestão responsável por consumir mensagens MQTT de telemetria, validar os payloads recebidos, resolver o `hardwareUuid` do sensor para o UUID interno do dispositivo e persistir medições válidas no PostgreSQL.

O worker deve ser desenvolvido em Java 25, com uso mínimo de frameworks. Ele ocupa a responsabilidade de persistência que antes era prevista no mock sensor. O mock sensor passa a publicar mensagens MQTT, enquanto o worker executa a etapa de ingestão e gravação.

## Escopo

Incluído nesta spec:

- Aplicação Java em `apps/mqtt-ingestor`.
- Runtime Java 25.
- Consumo de mensagens MQTT.
- Validação do payload JSON de telemetria.
- Resolução de `hardwareUuid` para `devices.uuid`.
- Cache em memória para evitar consultas repetidas do mesmo dispositivo.
- Persistência de medições válidas no PostgreSQL.
- Registro de falhas relevantes para diagnóstico.
- Execução prevista via Docker Compose.

Fora deste escopo inicial:

- API HTTP pública.
- Spring Boot ou frameworks pesados.
- Autenticação MQTT.
- TLS no broker MQTT.
- Dead-letter queue persistente.
- Retry sofisticado com backoff configurável.
- Deduplicação perfeita de mensagens.

## Comportamento esperado

Ao iniciar, o worker deve ler as variáveis de ambiente, conectar ao PostgreSQL e conectar ao broker MQTT.

Depois de conectado ao broker, o worker deve assinar o tópico de telemetria configurado. Para cada mensagem recebida, o worker deve:

1. Ler o payload como JSON UTF-8.
2. Validar os campos obrigatórios.
3. Validar formatos e tipos.
4. Resolver `hardwareUuid` para `devices.uuid`.
5. Verificar que o dispositivo está com status `ACTIVATED`.
6. Persistir a medição na tabela `measurements`.
7. Definir `received_at` com o horário de recebimento/processamento no worker.
8. Registrar sucesso ou falha em log.

Se uma mensagem for inválida, o worker deve registrar o erro e continuar consumindo novas mensagens. Uma mensagem inválida não deve encerrar o processo.

## Dados e contratos

### Tópico MQTT

Tópico padrão:

```text
sensorhub/telemetry
```

O worker deve assinar esse tópico por padrão. A primeira versão usa um único tópico compartilhado por todos os dispositivos. A introdução de tópicos por dispositivo deve ser documentada em spec futura, se necessária.

### Payload MQTT

Payload esperado:

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

Campos obrigatórios:

- `hardwareUuid`
- `temperature`
- `temperatureUnit`
- `humidity`
- `humidityUnit`
- `measuredAt`

O worker deve aceitar inicialmente:

- `temperatureUnit`: `CELSIUS`
- `humidityUnit`: `RELATIVE_PERCENT`

O campo `receivedAt` não deve ser aceito como entrada de confiança. Mesmo que venha no payload, o worker deve ignorá-lo e gerar `received_at` no momento do processamento.

### Variáveis de ambiente

Conexão MQTT:

- `SENSORHUB_MQTT_HOST`: host do broker MQTT. Padrão sugerido: `mqtt`.
- `SENSORHUB_MQTT_PORT`: porta do broker MQTT. Padrão sugerido: `1883`.
- `SENSORHUB_MQTT_TOPIC`: tópico de telemetria. Padrão: `sensorhub/telemetry`.
- `SENSORHUB_MQTT_CLIENT_ID`: identificador do consumidor MQTT. Padrão sugerido: `sensorhub-mqtt-ingestor`.
- `SENSORHUB_MQTT_QOS`: QoS usado na assinatura. Padrão inicial: `0`.

Conexão com PostgreSQL:

- `SENSORHUB_DB_HOST`: host do PostgreSQL. Padrão sugerido: `postgres`.
- `SENSORHUB_DB_PORT`: porta do PostgreSQL. Padrão sugerido: `5432`.
- `SENSORHUB_DB_NAME`: nome do banco. Padrão sugerido: `sensorhub`.
- `SENSORHUB_DB_USER`: usuário do banco. Padrão sugerido: `sensorhub`.
- `SENSORHUB_DB_PASSWORD`: senha do banco. Padrão sugerido: `sensorhub`.

Cache:

- `SENSORHUB_DEVICE_CACHE_TTL_SECONDS`: tempo opcional para expirar entradas do cache de dispositivos. Padrão sugerido: `300`.

### Cache de dispositivos

Formato conceitual do cache:

```text
{
  "b0fee3a6-ae91-4265-9365-36f793f32f06": {
    "deviceUuid": "uuid-interno-do-device",
    "status": "ACTIVATED"
  }
}
```

Consulta usada para resolver o dispositivo:

```sql
SELECT uuid, status
FROM devices
WHERE hardware_uuid = ?;
```

O worker deve persistir medições apenas para dispositivos com status `ACTIVATED`. Dispositivos `INACTIVATED` devem ser ignorados e registrados em log.

O cache deve evitar consultas repetidas a `devices`, mas não deve impedir que mudanças administrativas sejam refletidas. Por isso, a primeira implementação deve expirar entradas periodicamente usando `SENSORHUB_DEVICE_CACHE_TTL_SECONDS`.

### Inserção de medições

A inserção deve gravar pelo menos:

- `device_uuid`
- `temperature`
- `temperature_unit`
- `humidity`
- `humidity_unit`
- `measured_at`
- `received_at`

Exemplo conceitual:

```sql
INSERT INTO measurements (
    device_uuid,
    temperature,
    temperature_unit,
    humidity,
    humidity_unit,
    measured_at,
    received_at
) VALUES (?, ?, ?, ?, ?, ?, ?);
```

## Regras de negócio

- O payload MQTT deve identificar o dispositivo por `hardwareUuid`.
- O worker deve resolver `hardwareUuid` para `devices.uuid` antes de persistir qualquer medição.
- O worker não deve persistir `hardwareUuid` em `measurements`.
- O worker deve persistir medições usando o UUID interno em `measurements.device_uuid`.
- Apenas dispositivos com status `ACTIVATED` devem receber novas medições.
- Dispositivos `INACTIVATED` devem ter novas leituras descartadas, preservando o histórico existente.
- `received_at` deve ser definido pelo worker e não pelo sensor.
- `measured_at` deve representar o timestamp informado pelo sensor ou simulador.
- Temperatura e umidade devem ser numéricas.
- Temperatura deve armazenar a unidade recebida, inicialmente `CELSIUS`.
- Umidade deve armazenar a unidade recebida, inicialmente `RELATIVE_PERCENT`.
- A API pública continua sem endpoints para criar medições.
- O worker deve continuar processando mensagens depois de falhas isoladas de payload ou persistência.

## Erros e casos limite

- Payload que não é JSON válido deve ser descartado com log.
- Payload com campo obrigatório ausente deve ser descartado com log.
- `hardwareUuid` inválido deve ser descartado com log.
- `hardwareUuid` não encontrado em `devices` deve ser descartado com log.
- Dispositivo encontrado com status `INACTIVATED` deve ser ignorado com log.
- Unidade de temperatura ou umidade desconhecida deve ser rejeitada.
- `measuredAt` inválido deve ser rejeitado.
- Falha de conexão com PostgreSQL deve impedir a inicialização ou deixar o processo em retry controlado.
- Falha de conexão com broker MQTT deve impedir a inicialização ou deixar o processo em retry controlado.
- Falha temporária de insert deve ser registrada; o worker deve continuar processando mensagens seguintes quando possível.
- Mensagens duplicadas podem gerar medições duplicadas na primeira versão, pois o payload inicial não possui identificador único de evento.

## Critérios de aceite

- Existe uma aplicação Java em `apps/mqtt-ingestor`.
- A aplicação usa Java 25.
- A aplicação conecta ao broker MQTT configurado.
- A aplicação assina o tópico `sensorhub/telemetry` por padrão.
- A aplicação valida o payload JSON de telemetria.
- A aplicação resolve `hardwareUuid` para `devices.uuid`.
- A aplicação ignora dispositivos inexistentes ou `INACTIVATED`.
- A aplicação persiste medições válidas em `measurements`.
- A aplicação define `received_at` no momento do processamento.
- A aplicação mantém cache em memória com expiração para resolução de dispositivos.
- Existe configuração no Docker Compose para executar o worker.
- Existem testes ou verificação objetiva cobrindo parsing, validação, resolução de dispositivo e persistência.

## Testes

### Payload

- Testar parsing de JSON válido.
- Testar rejeição de JSON inválido.
- Testar rejeição de campos obrigatórios ausentes.
- Testar rejeição de UUID inválido.
- Testar rejeição de unidades desconhecidas.
- Testar rejeição de `measuredAt` inválido.

### Banco de dados

- Testar resolução de `hardwareUuid` para `deviceUuid`.
- Testar que dispositivos `INACTIVATED` não recebem novas medições.
- Testar inserção de medição usando `device_uuid` interno.
- Testar persistência de `received_at` definido pelo worker.

### MQTT

- Testar assinatura no tópico configurado.
- Testar processamento de mensagem recebida do broker.
- Testar que falha em uma mensagem não interrompe o consumo das próximas.

## Observações técnicas

- O worker deve usar Java 25.
- A implementação deve priorizar Java puro e bibliotecas pequenas.
- Uma biblioteca MQTT como Eclipse Paho pode ser usada para conexão e assinatura.
- O acesso ao PostgreSQL pode usar JDBC diretamente ou uma camada leve, evitando frameworks pesados.
- As migrations continuam sob responsabilidade da API com Flyway.
- Para execução local, o Docker Compose deve iniciar PostgreSQL, broker MQTT, API, worker e mock sensor.
- O worker deve iniciar depois do PostgreSQL saudável e depois da API iniciar, para reduzir risco de consultar tabelas antes da criação do schema.
- O serviço do worker pode usar `restart: on-failure` no Docker Compose para tolerar a corrida inicial enquanto a API aplica as migrations.
