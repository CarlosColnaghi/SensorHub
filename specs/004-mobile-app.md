# Mobile App

## Objetivo

Definir o aplicativo mobile Flutter inicial do SensorHub, responsável por permitir que o usuário cadastre dispositivos, organize ambientes, acompanhe leituras atuais em tempo quase real e consulte detalhes históricos com gráficos e overview.

O app deve consumir dados exclusivamente pela API. Processamentos de domínio, agregações, cálculo de máximas, mínimas, médias, status de atualização e preparação de séries para gráficos devem acontecer no backend.

## Escopo

Incluído nesta spec:

- Aplicativo Flutter em `apps/mobile`.
- Experiência visual priorizando dark mode.
- Tela inicial com cards de sensores e valores principais em destaque.
- Atualização periódica da tela inicial a partir da API.
- Tela de detalhe do sensor com leitura atual, metadados, gráficos temporais e overview.
- Menu de navegação para cadastro de dispositivos, cadastro de ambientes e perfil de usuário.
- Formulários para cadastrar e editar dispositivos.
- Formulários para cadastrar e editar ambientes.
- Tela de perfil de usuário usando os dados da API.
- Estados de carregamento, vazio, erro e sem atualização recente.

Fora deste escopo inicial:

- Autenticação e autorização.
- Push notifications.
- Alertas configuráveis.
- Funcionamento offline.
- Provisionamento automático de hardware.
- Comunicação direta com MQTT, PostgreSQL ou simulador Python.
- Processamento local de agregações históricas.

## Usuários e sistemas envolvidos

- Usuário final: consulta sensores, cadastra dispositivos, cria ambientes e visualiza perfil.
- App Flutter: renderiza a interface e consome contratos HTTP da API.
- API: fornece CRUD, leituras atuais, séries temporais, overview e status de atualização.
- Simulador Python: gera medições mockadas persistidas no PostgreSQL para desenvolvimento local.
- PostgreSQL: armazena usuários, ambientes, dispositivos e medições.

## Comportamento esperado

Ao abrir o app, a tela inicial deve listar os sensores disponíveis em cards. Cada card deve exibir pelo menos:

- nome do dispositivo;
- ambiente associado, quando existir;
- temperatura atual;
- umidade atual;
- horário da última atualização;
- status de atualização calculado pela API;
- indicação visual para dispositivo sem medição, desatualizado ou inativado.

A tela inicial deve separar dados estáveis de dados variáveis. Metadados de dispositivos e ambientes devem ser carregados sob demanda ou com menor frequência. Leituras atuais, última comunicação e status de atualização devem ser atualizados por polling em endpoint específico de medições. O app não deve consultar individualmente cada sensor para montar ou atualizar os cards da tela inicial.

Ao tocar em um card, o usuário deve ser levado para uma tela de detalhe do sensor. Essa tela deve exibir:

- nome do dispositivo;
- `hardwareUuid`;
- status operacional;
- ambiente associado;
- última medição;
- última comunicação;
- gráfico de temperatura em função do tempo;
- gráfico de umidade em função do tempo;
- overview do período selecionado.

Os gráficos devem receber da API uma série temporal já filtrada e preparada para renderização. Quando houver muitos pontos no período, a API deve ser responsável por agregação ou redução de pontos.

O overview do período deve vir pronto da API, incluindo pelo menos:

- temperatura máxima e timestamp em que ocorreu;
- temperatura mínima e timestamp em que ocorreu;
- umidade máxima e timestamp em que ocorreu;
- umidade mínima e timestamp em que ocorreu;
- média de temperatura;
- média de umidade;
- quantidade de medições consideradas;
- início e fim efetivos do intervalo.

O usuário deve conseguir alterar o período da tela de detalhe por controles simples, inicialmente:

- últimas 1 hora;
- últimas 6 horas;
- últimas 24 horas;
- últimos 7 dias;
- intervalo personalizado, quando a UI estiver pronta.

O app deve possuir um menu de navegação com acesso a:

- início;
- dispositivos;
- ambientes;
- perfil.

## Paleta e UI

O app deve ser desenhado para dark mode como experiência principal. A paleta inicial deve evitar uma aparência monocromática e manter contraste suficiente para leitura rápida.

Cores base:

- `background`: `#0B0F14`
- `surface`: `#121821`
- `surfaceAlt`: `#182231`
- `border`: `#273244`
- `textPrimary`: `#F4F7FB`
- `textSecondary`: `#A7B3C2`
- `textMuted`: `#738195`

Cores de marca e ação:

- `primary`: `#2DD4BF`
- `primaryStrong`: `#14B8A6`
- `accent`: `#60A5FA`
- `success`: `#22C55E`
- `warning`: `#F59E0B`
- `danger`: `#F43F5E`

Cores para dados:

- temperatura: `#F97316`
- umidade: `#38BDF8`
- sem dados: `#64748B`

Diretrizes de interface:

- Cards devem ter raio de borda máximo de 8px.
- Cards da tela inicial devem priorizar temperatura e umidade em destaque.
- Ícones devem ser usados em botões e itens de menu quando houver biblioteca disponível.
- Gráficos devem usar cores consistentes com temperatura e umidade.
- Estados críticos devem usar cor e texto, não apenas cor.
- Textos voltados ao usuário final devem estar em português.
- Identificadores, nomes de classes, rotas internas e código devem estar em inglês.

## Navegação

Rotas iniciais sugeridas:

- `/home`: tela inicial com cards de sensores.
- `/devices`: listagem e gerenciamento de dispositivos.
- `/devices/new`: cadastro de dispositivo.
- `/devices/{deviceUuid}`: detalhe do sensor.
- `/environments`: listagem e gerenciamento de ambientes.
- `/environments/new`: cadastro de ambiente.
- `/profile`: perfil do usuário.

A navegação principal deve usar uma barra inferior ou menu lateral, conforme o padrão visual escolhido na implementação. A primeira versão deve priorizar acesso rápido a início, dispositivos, ambientes e perfil.

## Dados e contratos

O app deve consumir os endpoints definidos em `specs/002-api.md`.

Para a tela inicial e a tela de detalhe, a API deve fornecer contratos próprios para evitar processamento local no app e para separar dados estáveis de dados variáveis:

- `GET /api/v1/users/{userUuid}/dashboard/devices`: retorna metadados estáveis dos dispositivos para montar os cards.
- `GET /api/v1/users/{userUuid}/dashboard/measurements/latest`: retorna leituras atuais, última comunicação e status de atualização para polling da home.
- `GET /api/v1/devices/{deviceUuid}`: retorna metadados estáveis para a tela de detalhe quando eles não estiverem disponíveis em cache.
- `GET /api/v1/devices/{deviceUuid}/measurements/overview?from={from}&to={to}&bucket={bucket}`: retorna leitura atual, série temporal e overview do período.

O app pode consumir os endpoints CRUD existentes para:

- listar, criar, editar e remover dispositivos;
- listar, criar, editar e remover ambientes;
- consultar e atualizar dados do usuário.

### Metadados do card

Exemplo de item retornado por `GET /api/v1/users/{userUuid}/dashboard/devices`:

```json
{
  "deviceUuid": "d1fb2c1a-2e7d-49e2-9aa6-3b196f7d27a0",
  "hardwareUuid": "2f4a7d8e-3b6a-4a5c-9f2b-8f4d0d8f3c21",
  "name": "Sensor da sala",
  "environmentUuid": "bf3c99bb-0ab1-44e4-a75a-1d8f3575e9a1",
  "environmentName": "Sala",
  "deviceStatus": "ACTIVATED"
}
```

### Leituras atuais do card

Exemplo de item retornado por `GET /api/v1/users/{userUuid}/dashboard/measurements/latest`:

```json
{
  "deviceUuid": "d1fb2c1a-2e7d-49e2-9aa6-3b196f7d27a0",
  "freshnessStatus": "ONLINE",
  "lastSeenAt": "2026-06-04T18:15:00Z",
  "latestMeasurement": {
    "temperature": 24.7,
    "temperatureUnit": "CELSIUS",
    "humidity": 58.2,
    "humidityUnit": "RELATIVE_PERCENT",
    "measuredAt": "2026-06-04T18:15:00Z"
  }
}
```

Quando um sensor não possuir medições, `latestMeasurement` deve vir nulo e o card deve renderizar estado sem dados.

`freshnessStatus` deve ser calculado pela API. Valores iniciais:

- `ONLINE`: dispositivo ativo com leitura recente.
- `STALE`: dispositivo ativo, mas sem leitura dentro da janela configurada pela API.
- `NO_DATA`: dispositivo ativo sem medições.
- `INACTIVATED`: dispositivo inativado administrativamente.

### Detalhe do sensor

Metadados estáveis do sensor devem vir de `GET /api/v1/devices/{deviceUuid}` ou do cache populado pelo dashboard de dispositivos. Dados variáveis da tela de detalhe devem vir de `GET /api/v1/devices/{deviceUuid}/measurements/overview`.

Na tela de detalhe, `latestMeasurement` representa a leitura atual do dispositivo. `series` e `overview` representam o período selecionado pelo usuário.

O app não deve usar `GET /api/v1/devices/{deviceUuid}/measurements` para montar gráficos ou calcular overview. Esse endpoint paginado retorna medições brutas e deve ficar reservado para uma futura tela de histórico tabular, exportação ou depuração.

Exemplo de resposta esperada para o overview de medições:

```json
{
  "deviceUuid": "d1fb2c1a-2e7d-49e2-9aa6-3b196f7d27a0",
  "freshnessStatus": "ONLINE",
  "lastSeenAt": "2026-06-04T18:15:00Z",
  "period": {
    "from": "2026-06-04T12:15:00Z",
    "to": "2026-06-04T18:15:00Z",
    "bucket": "5m"
  },
  "latestMeasurement": {
    "temperature": 24.7,
    "temperatureUnit": "CELSIUS",
    "humidity": 58.2,
    "humidityUnit": "RELATIVE_PERCENT",
    "measuredAt": "2026-06-04T18:15:00Z"
  },
  "series": [
    {
      "timestamp": "2026-06-04T18:10:00Z",
      "temperature": 24.5,
      "humidity": 59.1
    }
  ],
  "overview": {
    "temperatureMax": 26.8,
    "temperatureMaxAt": "2026-06-04T15:25:00Z",
    "temperatureMin": 22.9,
    "temperatureMinAt": "2026-06-04T12:40:00Z",
    "temperatureAverage": 24.6,
    "humidityMax": 63.4,
    "humidityMaxAt": "2026-06-04T13:10:00Z",
    "humidityMin": 52.8,
    "humidityMinAt": "2026-06-04T17:45:00Z",
    "humidityAverage": 58.7,
    "measurementCount": 73
  }
}
```

## Regras de negócio

- O app deve usar a API como única fonte de dados.
- O app não deve acessar PostgreSQL, MQTT ou arquivos do simulador.
- O app não deve calcular máximas, mínimas, médias, status de atualização ou agregações de gráfico.
- O app pode calcular apenas formatações de apresentação, como texto de data relativo, máscara visual e escolha de ícone.
- O app deve tratar `204 No Content` e listas vazias como estado vazio, não como erro fatal.
- O app deve permitir cadastrar dispositivo informando `hardwareUuid`, nome opcional, usuário e ambiente opcional.
- O app deve permitir associar ou desassociar dispositivo de ambiente.
- O app deve permitir criar e editar ambientes.
- O app deve exibir claramente quando um sensor está sem dados, desatualizado ou inativado.
- O intervalo de atualização da home deve ser configurável no código, com padrão inicial sugerido de 5 segundos.
- O app deve evitar disparar múltiplas requisições simultâneas para atualizar a mesma tela.

## Estados de erro

- Falha de conexão com API deve exibir estado de erro com ação de tentar novamente.
- API indisponível deve exibir mensagem clara e preservar a navegação.
- Erro de validação no cadastro deve ser exibido próximo ao campo correspondente quando possível.
- Conflito ao cadastrar `hardwareUuid` duplicado deve ser exibido como erro de dispositivo já cadastrado.
- Sensor sem medições deve exibir estado vazio nos valores e gráficos.
- Intervalo sem dados deve exibir gráfico vazio e overview indisponível.
- Sessão de usuário ainda não autenticada deve usar o usuário seedado ou seleção local temporária enquanto autenticação estiver fora de escopo.

## Critérios de aceite

- Existe scaffold Flutter em `apps/mobile`.
- O app possui tema dark mode com a paleta definida nesta spec.
- A tela inicial exibe cards de sensores consumidos da API.
- A tela inicial atualiza os cards periodicamente.
- O app navega do card para o detalhe do sensor.
- A tela de detalhe exibe leitura atual, gráfico de temperatura, gráfico de umidade e overview vindos da API.
- O app possui menu para início, dispositivos, ambientes e perfil.
- O app permite cadastrar dispositivo por `hardwareUuid`.
- O app permite cadastrar ambiente.
- O app permite associar dispositivo a ambiente.
- O app exibe estados de carregamento, vazio e erro.
- O app não implementa agregações históricas ou cálculo de overview localmente.
- Existe configuração prevista no Docker Compose quando houver execução aplicável ao app ou suporte de desenvolvimento local.
- Existem testes de widget ou integração para os principais fluxos.

## Testes

### Interface

- Testar renderização da tela inicial com lista de cards.
- Testar card de sensor com leitura atual.
- Testar card de sensor sem medições.
- Testar card de sensor desatualizado.
- Testar navegação do card para tela de detalhe.
- Testar renderização da tela de detalhe com gráficos e overview.
- Testar estados de carregamento, vazio e erro.

### Formulários

- Testar cadastro de dispositivo com `hardwareUuid` válido.
- Testar erro de `hardwareUuid` inválido ou duplicado.
- Testar criação de ambiente.
- Testar associação de dispositivo a ambiente.
- Testar atualização de perfil.

### Integração com API

- Testar parsing do contrato de dashboard de dispositivos.
- Testar parsing do contrato de overview de medições.
- Testar que a home usa o endpoint de metadados para dados estáveis.
- Testar que a home usa o endpoint de leituras atuais para polling dos dados variáveis.
- Testar que o app não dispara uma requisição por sensor para atualizar a home.
- Testar que o overview é lido da resposta da API.

## Impacto técnico previsto

- Criar estrutura Flutter em `apps/mobile`.
- Definir camada de cliente HTTP para a API.
- Definir modelos DTO para contratos da API.
- Definir componentes de UI para cards, gráficos, formulários e estados.
- Escolher biblioteca de gráficos Flutter leve e mantida.
- Adicionar configuração de URL base da API por ambiente.
- Atualizar Docker Compose ou documentação de execução local quando o scaffold Flutter for criado.

## Observações técnicas

- A primeira versão pode usar polling HTTP para atualização em tempo quase real.
- Uma evolução futura pode usar Server-Sent Events ou WebSocket, desde que a API passe a expor esse contrato.
- Enquanto autenticação estiver fora de escopo, o app deve usar usuário configurável ou seedado para desenvolvimento local.
- A spec da API deve incluir endpoints separados para metadados do dashboard, leituras atuais e overview de medições antes da implementação do app.
