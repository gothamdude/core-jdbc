# com.gothamdude.core-jdbc

A reusable Spring-JDBC wrapper library (not a runnable application) that provides base classes for CRUD repositories using `NamedParameterJdbcTemplate`. 
Targets Java 21, Spring 6.2, Spring Boot 3.5. 
Packaged as a JAR for downstream Spring Boot projects.


## Build & Test Commands

```bash
mvn clean install          # build + run all tests
mvn test                   # run all tests
mvn test -Dtest=ClassName  # run a single test class
mvn test -Dtest=ClassName#methodName  # run a single test method
```
Surefire requires JVM args `-XX:+EnableDynamicAgentLoading -Xshare:off` (already configured in pom.xml).

## Architecture

### Entity Hierarchy

`BaseEntity<ID>` (abstract, generic ID) is the root. Two branches:

- **DomainEntity<ID>** — long-lived records with audit fields (`Auditable`: created/updated timestamps and users) and soft-delete (`Activable`: activeFlag). Subclass this for reference/master data tables.
- **TransactionEntity** — append-only records with `Long` ID and creation-only audit (createdTs, createdBy). Uses Lombok `@Getter/@Setter`.

### Repository Pattern

`BaseEntityRepository<T, ID>` defines the CRUD contract. `AbstractJdbcRepository<T, ID>` implements it via `NamedParameterJdbcTemplate`. Only `getTableName()` and `getRowMapper()` are abstract — all SQL/param methods default to `UnsupportedOperationException`, so concrete repos override only the operations they need.

All errors are wrapped in `CoreJdbcException` with a typed `ErrorCode` enum.

### Auto-Configuration

`DbConfig` is a Spring Boot `@AutoConfiguration` that reads `app.database.*` properties via `DbProperties` and creates HikariCP DataSource, JdbcTemplate, NamedParameterJdbcTemplate, and transaction manager beans. All beans are `@ConditionalOnMissingBean`, so downstream projects can override them.

## Testing Conventions

- **Unit tests** (e.g., `AbstractJdbcRepositoryTest`): Mockito + JUnit 5 + AssertJ. Mock `NamedParameterJdbcTemplate`, test each repo method in isolation.
- **Integration tests** (e.g., `AbstractJdbcRepositoryIntegrationTest`): `@SpringJUnitConfig` with an embedded H2 database, `@Transactional @Rollback` for test isolation. Schema lives in `src/test/resources/test-schema.sql`. Define a concrete inner-class entity and repository for testing.
- Test config uses `app.database.*` properties from `src/test/resources/application-test.properties`.

## Key Conventions

- Lombok is used for boilerplate (`@Data`, `@Getter`, `@Setter`, `@RequiredArgsConstructor`, `@Slf4j`). Lombok's `@Generated` annotation is enabled via `lombok.config` for JaCoCo exclusion.
- Use `NamedParameterJdbcTemplate` (not plain `JdbcTemplate`) for all repository queries.
- SQL is returned from overridable `get*Sql()` methods, parameters from `get*Params()` methods returning `MapSqlParameterSource`.