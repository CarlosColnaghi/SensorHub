# SensorHub API

API Java 25/Spring Boot para gerenciamento de usuários, ambientes, dispositivos e consulta de medições.

## Stack

- Java 25
- Spring Boot 4.0.6
- Spring Web MVC
- Spring Data JPA
- Flyway
- PostgreSQL

## Executar localmente

Com PostgreSQL disponível:

```text
mvn spring-boot:run
```

Também é possível subir pelo Docker Compose na raiz do repositório:

```text
docker compose up api postgres
```

## Testes

```text
mvn test
```

Os testes usam Testcontainers com PostgreSQL para validar migrations, constraints e contratos HTTP.
