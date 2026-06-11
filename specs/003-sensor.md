# Sensor

## Objetivo

Definir o script Python responsável por mockar os dados de um sensor, gerando medições simuladas e publicando-as em um broker MQTT. O objetivo é permitir desenvolver e testar o fluxo de ingestão sem depender de sensor físico real.

O sensor simulado não deve interagir diretamente com o PostgreSQL. Ele deve simular apenas o comportamento de um dispositivo publicando telemetria no tópico MQTT configurado. A validação do dispositivo, resolução de `hardwareUuid` para `devices.uuid` e persistência das medições ficam sob responsabilidade do ingestor definido em `specs/005-ingestor.md`.

## Escopo

Incluído nesta spec:

- Script Python em `apps/sensor`.
- Configuração por variáveis de ambiente.
- Conexão com broker MQTT.
- Publicação contínua de mensagens MQTT a cada intervalo configurável.
- Geração de temperatura e umidade com variação aleatória dentro de ranges definidos.
- Publicação do payload JSON inicial de sensor.
- Execução prevista via Docker Compose.

Fora deste escopo:

- Conexão direta com PostgreSQL.
- Consulta à tabela `devices`.
- Resolução de `hardwareUuid` para `devices.uuid`.
- Validação de status `ACTIVATED` ou `INACTIVATED`.
- Persistência de medições.
- Consumo de mensagens MQTT.

## Comportamento esperado

Ao iniciar, o script deve ler as variáveis de ambiente, validar a configuração e conectar ao broker MQTT configurado.

Depois de conectado, o script deve preparar a lista de `hardwareUuid` que serão simulados. Cada `hardwareUuid` representa um sensor físico ou simulado conhecido pelo sistema. O script não deve verificar se esses UUIDs existem no banco.

Após a preparação, o script deve entrar em loop e, a cada intervalo configurado, gerar uma medição para cada dispositivo configurado.

Cada mensagem publicada deve:

- usar o `hardwareUuid` configurado;
- gerar temperatura aleatória;
- gerar umidade aleatória;
- usar `CELSIUS` como `temperatureUnit`;
- usar `RELATIVE_PERCENT` como `humidityUnit`;
- definir `measuredAt` com o timestamp atual em ISO 8601 UTC;
- ser publicada no tópico MQTT configurado.

O loop deve continuar até o processo ser interrompido.

## Dados e contratos

### Tópico MQTT

Tópico padrão:

```text
sensorhub/measurements
```

O tópico pode ser alterado por variável de ambiente. A primeira versão deve usar um único tópico compartilhado por todos os sensores simulados. Tópicos por dispositivo podem ser adicionados depois, se houver necessidade operacional.

### Payload MQTT

Cada mensagem deve usar JSON UTF-8 com o contrato inicial do sensor:

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

- `hardwareUuid`: UUID físico do dispositivo simulado.
- `temperature`: temperatura simulada.
- `temperatureUnit`: unidade da temperatura, inicialmente `CELSIUS`.
- `humidity`: umidade simulada.
- `humidityUnit`: unidade da umidade, inicialmente `RELATIVE_PERCENT`.
- `measuredAt`: timestamp da medição gerado pelo simulador.

O campo `receivedAt` não deve ser publicado pelo sensor simulado. Ele deve ser definido pelo ingestor no momento da persistência.

### Variáveis de ambiente

Conexão MQTT:

- `SENSORHUB_MQTT_HOST`: host do broker MQTT. Padrão sugerido: `mqtt`.
- `SENSORHUB_MQTT_PORT`: porta do broker MQTT. Padrão sugerido: `1883`.
- `SENSORHUB_MQTT_TOPIC`: tópico de telemetria. Padrão: `sensorhub/measurements`.
- `SENSORHUB_MQTT_CLIENT_ID`: identificador do cliente MQTT. Padrão sugerido: `sensorhub-sensor`.
- `SENSORHUB_MQTT_QOS`: QoS usado na publicação. Padrão inicial: `0`.

Dispositivos simulados:

- `SENSORHUB_HARDWARE_UUIDS`: lista de `hardwareUuid` separados por vírgula. Deve aceitar um único UUID. Padrão sugerido: `b0fee3a6-ae91-4265-9365-36f793f32f06`.

Intervalo:

- `SENSORHUB_MEASUREMENT_INTERVAL_SECONDS`: intervalo entre gerações, em segundos. Padrão: `5`.

Temperatura:

- `SENSORHUB_TEMPERATURE_MIN`: temperatura mínima. Padrão: `18.0`.
- `SENSORHUB_TEMPERATURE_MAX`: temperatura máxima. Padrão: `32.0`.
- `SENSORHUB_TEMPERATURE_STEP_MAX`: variação máxima por ciclo. Padrão: `0.4`.

Umidade:

- `SENSORHUB_HUMIDITY_MIN`: umidade mínima. Padrão: `35.0`.
- `SENSORHUB_HUMIDITY_MAX`: umidade máxima. Padrão: `80.0`.
- `SENSORHUB_HUMIDITY_STEP_MAX`: variação máxima por ciclo. Padrão: `1.5`.

## Regras de negócio

- O script deve usar `hardwareUuid` como identificador do sensor publicado no payload.
- O script não deve conhecer nem usar o UUID interno de `devices.uuid`.
- O script não deve acessar PostgreSQL.
- O script não deve filtrar dispositivos por status administrativo.
- Temperatura e umidade devem permanecer dentro dos ranges configurados.
- Valores consecutivos devem variar de forma gradual, respeitando `SENSORHUB_TEMPERATURE_STEP_MAX` e `SENSORHUB_HUMIDITY_STEP_MAX`.
- O primeiro valor gerado para cada dispositivo pode ser aleatório dentro do range completo.
- Valores subsequentes devem partir do valor anterior e aplicar uma variação aleatória pequena.
- Temperatura deve ser arredondada para duas casas decimais.
- Umidade deve ser arredondada para duas casas decimais.
- O intervalo padrão entre ciclos deve ser de 5 segundos.
- O sensor simulado deve publicar somente mensagens de telemetria; qualquer persistência deve acontecer no ingestor MQTT.

## Erros e casos limite

- `SENSORHUB_HARDWARE_UUIDS` ausente deve usar o UUID seedado pela API como padrão.
- `hardwareUuid` inválido deve impedir a inicialização e registrar erro claro.
- Falha de conexão com o broker MQTT deve encerrar o processo com erro claro ou tentar reconectar conforme a biblioteca MQTT usada.
- Falha temporária de publicação deve ser registrada e o script deve tentar continuar no próximo ciclo quando possível.
- Intervalo menor ou igual a zero deve ser rejeitado.
- Ranges inválidos, como mínimo maior que máximo, devem ser rejeitados.
- Steps negativos devem ser rejeitados.
- QoS diferente de `0`, `1` ou `2` deve ser rejeitado.

## Critérios de aceite

- Existe um script Python em `apps/sensor`.
- O script lê configuração por variáveis de ambiente.
- O script aceita `SENSORHUB_HARDWARE_UUIDS` com um ou mais UUIDs separados por vírgula.
- O script conecta ao broker MQTT configurado.
- O script publica mensagens no tópico `sensorhub/measurements` por padrão.
- O payload publicado segue o contrato JSON definido nesta spec.
- O script não abre conexão com PostgreSQL.
- O script gera temperatura e umidade dentro dos ranges configurados.
- O script mantém variações consecutivas próximas, respeitando os steps máximos configurados.
- O intervalo padrão de geração é 5 segundos.
- Existe configuração no Docker Compose para executar o broker MQTT.
- Existe configuração no Docker Compose para executar o sensor simulado apontando para o broker MQTT.
- Existem testes ou verificação objetiva cobrindo configuração, geração de valores e serialização/publicação do payload.

## Testes

### Configuração

- Testar parsing de `SENSORHUB_HARDWARE_UUIDS`.
- Testar default do hardware UUID seedado.
- Testar rejeição de UUID inválido.
- Testar rejeição de intervalo inválido.
- Testar rejeição de ranges inválidos.
- Testar rejeição de QoS inválido.

### Geração de dados

- Testar que temperatura fica dentro do range.
- Testar que umidade fica dentro do range.
- Testar que valores consecutivos respeitam o step máximo configurado.
- Testar arredondamento para duas casas decimais.
- Testar geração de `measuredAt` em formato ISO 8601 UTC.

### MQTT

- Testar serialização do payload JSON.
- Testar publicação no tópico configurado usando mock ou broker local de teste.
- Testar que o payload não inclui `deviceUuid` nem `receivedAt`.
- Testar comportamento quando a publicação falha.

## Observações técnicas

- O script deve usar uma biblioteca MQTT adequada para Python, como `paho-mqtt`.
- A implementação deve evitar frameworks pesados.
- O processo pode ser um loop simples com `time.sleep`.
- Para execução local, o Docker Compose deve iniciar o broker MQTT antes do sensor simulado.
- O serviço do sensor simulado pode usar `restart: on-failure` no Docker Compose para tolerar indisponibilidade temporária do broker.
- O script deve ser útil para desenvolvimento local, demos e geração de massa de dados.
