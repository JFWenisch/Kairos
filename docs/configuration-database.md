# Configuration: Database

This page covers database setup for Kairos.

## H2 (default)

H2 file mode works out of the box and is suitable for local and small setups.

```properties
spring.datasource.url=jdbc:h2:file:./kairos;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

The H2 web console is available at `http://localhost:8080/h2-console` when `spring.h2.console.enabled=true`.

## PostgreSQL

For production workloads, PostgreSQL is recommended.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/kairos
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.username=kairos
spring.datasource.password=secret
spring.jpa.hibernate.ddl-auto=update
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
```

Environment variable equivalent:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/kairos
SPRING_DATASOURCE_USERNAME=kairos
SPRING_DATASOURCE_PASSWORD=secret
```

Example `docker-compose` with PostgreSQL:

```yaml
version: "3.9"
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: kairos
      POSTGRES_USER: kairos
      POSTGRES_PASSWORD: secret
    volumes:
      - pgdata:/var/lib/postgresql/data

  kairos:
    image: ghcr.io/wenisch-tech/kairos:latest
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/kairos
      SPRING_DATASOURCE_USERNAME: kairos
      SPRING_DATASOURCE_PASSWORD: secret
    depends_on:
      - db

volumes:
  pgdata:
```

## Flyway Migrations

Kairos uses Flyway for startup migrations.

- Existing databases without a Flyway history table are baselined automatically.
- Pending migrations are then applied in order.
- No manual SQL migration steps are required for normal upgrades.
