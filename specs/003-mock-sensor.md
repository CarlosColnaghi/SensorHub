# Mock Sensor

## Objetivo

Definir o script Python inicial responsável por gerar medições simuladas e persisti-las no PostgreSQL, permitindo desenvolver e testar a API e o app sem depender de sensor físico, broker MQTT ou worker real.

O script deve simular o trecho inicial do fluxo de ingestão: receber ou conhecer um `hardwareUuid`, resolver esse identificador físico para o `device_uuid` interno do banco, gerar valores de temperatura e umidade e inserir registros na tabela `measurements`.

## Escopo

Incluído nesta spec:

- Script Python em `apps/mock-sensor`.
- Configuração por variáveis de ambiente.
- Resolução de `hardwareUuid` para `devices.uuid`.
- Cache em memória para evitar consulta repetida do mesmo dispositivo.
- Geração contínua de medições a cada intervalo configurável.
- Geração de temperatura e umidade com variação aleatória dentro de ranges definidos.
- Persistência direta em PostgreSQL.
- Atualização de `devices.last_seen_at` para refletir a última medição simulada.
- Execução prevista via Docker Compose.

## Comportamento esperado

Ao iniciar, o script deve ler as variáveis de ambiente, abrir conexão com o PostgreSQL e preparar a lista de `hardwareUuid` que serão simulados.

Para cada `hardwareUuid` configurado, o script deve consultar a tabela `devices` e resolver o UUID físico para o UUID interno do dispositivo:

- entrada: `devices.hardware_uuid`;
- saída/cache: `devices.uuid`.

Essa resolução deve ficar em memória por meio de um hashmap ou dicionário Python, usando `hardwareUuid` como chave e `deviceUuid` como valor. Depois que um dispositivo for resolvido, o script não deve consultar o banco novamente para esse mesmo `hardwareUuid` enquanto o processo estiver rodando.

Após resolver os dispositivos, o script deve entrar em loop e, a cada intervalo configurado, gerar uma medição para cada dispositivo configurado.

Cada medição deve:

- usar o `device_uuid` interno resolvido pelo cache;
- gerar temperatura aleatória;
- gerar umidade aleatória;
- usar `CELSIUS` como `temperature_unit`;
- usar `RELATIVE_PERCENT` como `humidity_unit`;
- definir `measured_at` com o timestamp atual;
- deixar `received_at` ser definido pelo banco ou definir explicitamente com o mesmo instante de recebimento do script;
- atualizar `devices.last_seen_at` com o timestamp de recebimento.

O loop deve continuar até o processo ser interrompido.

## Dados e contratos

### Variáveis de ambiente

Conexão com PostgreSQL:

- `SENSORHUB_DB_HOST`: host do PostgreSQL. Padrão sugerido: `postgres`.
- `SENSORHUB_DB_PORT`: porta do PostgreSQL. Padrão sugerido: `5432`.
- `SENSORHUB_DB_NAME`: nome do banco. Padrão sugerido: `sensorhub`.
- `SENSORHUB_DB_USER`: usuário do banco. Padrão sugerido: `sensorhub`.
- `SENSORHUB_DB_PASSWORD`: senha do banco. Padrão sugerido: `sensorhub`.

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

### Cache de dispositivos

Formato conceitual do cache:

```text
{
  "b0fee3a6-ae91-4265-9365-36f793f32f06": "uuid-interno-do-device"
}
```

Consulta usada para resolver o dispositivo:

```sql
SELECT uuid
FROM devices
WHERE hardware_uuid = ?
  AND status = 'ACTIVATED';
```

O script deve considerar apenas dispositivos com status `ACTIVATED`. Dispositivos `INACTIVATED` não devem receber novas medições simuladas.

### Inserção de medições

A inserção deve gravar pelo menos:

- `device_uuid`
- `temperature`
- `temperature_unit`
- `humidity`
- `humidity_unit`
- `measured_at`

Exemplo conceitual:

```sql
INSERT INTO measurements (
    device_uuid,
    temperature,
    temperature_unit,
    humidity,
    humidity_unit,
    measured_at
) VALUES (?, ?, 'CELSIUS', ?, 'RELATIVE_PERCENT', ?);
```

Depois da inserção, o script deve atualizar o dispositivo no mesmo ciclo:

```sql
UPDATE devices
SET last_seen_at = ?
WHERE uuid = ?;
```

## Regras de negócio

- O script deve usar `hardwareUuid` como entrada de configuração e persistir medições usando `device_uuid` interno.
- O script não deve usar `hardwareUuid` como chave estrangeira em `measurements`.
- O cache `hardwareUuid -> deviceUuid` deve evitar consultas repetidas a `devices` para UUIDs já resolvidos.
- Apenas dispositivos com status `ACTIVATED` devem receber medições simuladas.
- Temperatura e umidade devem permanecer dentro dos ranges configurados.
- Valores consecutivos devem variar de forma gradual, respeitando `SENSORHUB_TEMPERATURE_STEP_MAX` e `SENSORHUB_HUMIDITY_STEP_MAX`.
- O primeiro valor gerado para cada dispositivo pode ser aleatório dentro do range completo.
- Valores subsequentes devem partir do valor anterior e aplicar uma variação aleatória pequena.
- Temperatura deve ser arredondada para duas casas decimais.
- Umidade deve ser arredondada para duas casas decimais.
- O intervalo padrão entre ciclos deve ser de 5 segundos.
- O script deve atualizar `devices.last_seen_at` para permitir que a API indique última comunicação.
- A persistência da medição e a atualização de `last_seen_at` devem acontecer na mesma transação quando possível.

## Erros e casos limite

- `SENSORHUB_HARDWARE_UUIDS` ausente deve usar o UUID seedado pela API como padrão.
- `hardwareUuid` inválido deve impedir a inicialização e registrar erro claro.
- `hardwareUuid` não encontrado em `devices` deve impedir a inicialização ou remover esse UUID da simulação, registrando erro claro.
- Dispositivo encontrado com status `INACTIVATED` deve ser ignorado e registrado em log.
- Falha de conexão com PostgreSQL deve encerrar o processo com erro claro.
- Falha temporária de insert deve ser registrada e o script deve tentar continuar no próximo ciclo.
- Intervalo menor ou igual a zero deve ser rejeitado.
- Ranges inválidos, como mínimo maior que máximo, devem ser rejeitados.
- Steps negativos devem ser rejeitados.

## Critérios de aceite

- Existe um script Python em `apps/mock-sensor`.
- O script lê configuração por variáveis de ambiente.
- O script aceita `SENSORHUB_HARDWARE_UUIDS` com um ou mais UUIDs separados por vírgula.
- O script resolve `hardwareUuid` para `devices.uuid`.
- O script mantém cache em memória para o mapeamento `hardwareUuid -> deviceUuid`.
- O script insere registros válidos na tabela `measurements`.
- O script atualiza `devices.last_seen_at`.
- O script gera temperatura e umidade dentro dos ranges configurados.
- O script mantém variações consecutivas próximas, respeitando os steps máximos configurados.
- O intervalo padrão de geração é 5 segundos.
- Existe configuração no Docker Compose para executar o mock sensor.
- Existem testes ou verificação objetiva cobrindo configuração, resolução de dispositivo, geração de valores e persistência.

## Testes

### Configuração

- Testar parsing de `SENSORHUB_HARDWARE_UUIDS`.
- Testar default do hardware UUID seedado.
- Testar rejeição de UUID inválido.
- Testar rejeição de intervalo inválido.
- Testar rejeição de ranges inválidos.

### Geração de dados

- Testar que temperatura fica dentro do range.
- Testar que umidade fica dentro do range.
- Testar que valores consecutivos respeitam o step máximo configurado.
- Testar arredondamento para duas casas decimais.

### Banco de dados

- Testar resolução de `hardwareUuid` para `deviceUuid`.
- Testar que dispositivos `INACTIVATED` não entram no cache ativo.
- Testar inserção de medição usando `device_uuid` interno.
- Testar atualização de `devices.last_seen_at`.

## Observações técnicas

- O script deve usar biblioteca PostgreSQL adequada para Python, como `psycopg`.
- A implementação deve evitar frameworks pesados.
- O processo pode ser um loop simples com `time.sleep`.
- Para execução local, o Docker Compose deve depender do PostgreSQL saudável antes de iniciar o mock sensor.
- Como as migrations Flyway iniciais ficam na API, o serviço do mock sensor deve iniciar depois do container da API para reduzir risco de consultar tabelas antes da criação do schema.
- O serviço do mock sensor pode usar `restart: on-failure` no Docker Compose para tolerar a corrida inicial enquanto a API aplica as migrations.
- O script deve ser útil para desenvolvimento local, demos e geração de massa de dados.
