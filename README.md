# core-jdbc

A small, reusable **Spring‑JDBC** wrapper library for Spring‑Boot projects. It gives you a
template‑method repository base class, a set of composable entity *capability mixins*, automatic
audit stamping, classified exception translation, paging, soft delete, optimistic locking, batch
writes and database‑generated keys — **without** pulling in an ORM (no Hibernate, no JPA).

- **Group / Artifact / Version:** `com.gothamdude:core-jdbc:2.0.0`
- **Java:** 21
- **Spring Framework:** 6.2 · **Spring Boot autoconfigure:** 3.5.x
- **Packaging:** plain JAR, Spring‑Boot auto‑configured

> Looking for copy‑paste examples (CSV import, event API, auction)? See
> [`README_each_use_case.md`](./README_each_use_case.md).

---

## Table of contents

1. [Why it exists](#why-it-exists)
2. [Design in one picture](#design-in-one-picture)
3. [Getting started](#getting-started)
4. [Configuration](#configuration)
5. [The entity model](#the-entity-model)
6. [Writing a repository](#writing-a-repository)
7. [Reserved parameter names (automatic audit)](#reserved-parameter-names-automatic-audit)
8. [Repository API reference](#repository-api-reference)
9. [Extension hooks reference](#extension-hooks-reference)
10. [Error handling](#error-handling)
11. [Health checks](#health-checks)
12. [Composite keys](#composite-keys)
13. [Beans provided by auto‑configuration](#beans-provided-by-auto-configuration)

---

## Why it exists

A plain `NamedParameterJdbcTemplate` repository re‑implements the same plumbing in every method:
`try` / `catch` / log / wrap, audit‑column handling, soft‑delete semantics, key extraction. Those
invariants drift between tables and between projects.

`core-jdbc` moves that plumbing into one base class that **programs against capability interfaces**
(`CreateAudit`, `UpdateAudit`, `SoftDeletable`, `Versionable`) rather than against concrete entity
classes. A repository declares *what* its table looks like (SQL + a row mapper); the base class
supplies consistent *behavior*. Adding a new domain becomes a new row in a composition matrix, not a
change to the core.

---

## Design in one picture

```
BaseEntity<ID>            identity only: getId() / setId()
   ▲
   │  compose only what a table needs
   │
Capability mixins (the real contract the repository dispatches on)
   ├─ CreateAudit      createdTs, createdBy
   ├─ UpdateAudit      updatedTs, updatedBy
   ├─ Auditable        = CreateAudit + UpdateAudit
   ├─ SoftDeletable    active
   └─ Versionable      version

Pre-composed conveniences (not the only choice)
   ├─ AuditedEntity<ID>    = Auditable                  (created then updated records)
   └─ DomainEntity<ID>     = Auditable + SoftDeletable

AbstractJdbcRepository<T extends BaseEntity<ID>, ID>  implements BaseEntityRepository<T, ID>
   dispatches on  instanceof CreateAudit / UpdateAudit / SoftDeletable / Versionable
   — never on AuditedEntity or DomainEntity.
```

The last line is the whole point: the base class reacts to the **mixins an entity implements**, so a
brand‑new domain inherits working audit/soft‑delete/version behavior just by implementing the
interfaces it needs.

---

## Getting started

### 1. Depend on the library

```xml
<dependency>
    <groupId>com.gothamdude</groupId>
    <artifactId>core-jdbc</artifactId>
    <version>2.0.0</version>
</dependency>
```

Add a JDBC driver (e.g. `org.postgresql:postgresql`) on the consuming project's classpath.

### 2. Point it at a database

```properties
app.database.url=jdbc:postgresql://localhost:5432/appdb
app.database.username=appuser
app.database.password=secret
app.database.driver-class-name=org.postgresql.Driver
```

Auto‑configuration wires a HikariCP `DataSource`, a `JdbcTemplate`, a `NamedParameterJdbcTemplate`
and a `DataSourceTransactionManager` for you (see [Beans](#beans-provided-by-auto-configuration)).

### 3. Write an entity and a repository

```java
@Getter @Setter                                   // Lombok generates all accessors
public class Trade extends BaseEntity<Long> implements CreateAudit {   // append-only, create-audit
    private Long id;             // satisfies BaseEntity.getId/setId
    private String symbol;
    private BigDecimal price;
    private Instant createdTs;   // CreateAudit
    private String createdBy;    // CreateAudit
}

@Repository
public class TradeRepository extends AbstractJdbcRepository<Trade, Long> {

    public TradeRepository(NamedParameterJdbcTemplate template) { super(template); }

    @Override protected String getTableName() { return "trade"; }

    @Override protected RowMapper<Trade> getRowMapper() {
        return (rs, i) -> {
            Trade t = new Trade();
            t.setId(rs.getLong("id"));
            t.setSymbol(rs.getString("symbol"));
            t.setPrice(rs.getBigDecimal("price"));
            t.setCreatedTs(rs.getTimestamp("created_ts").toInstant());
            t.setCreatedBy(rs.getString("created_by"));
            return t;
        };
    }

    @Override protected String getInsertSql() {
        return "INSERT INTO trade (symbol, price, created_ts, created_by) " +
               "VALUES (:symbol, :price, :createdTs, :createdBy)";
    }

    @Override protected MapSqlParameterSource getInsertParams(Trade t) {
        return new MapSqlParameterSource()
                .addValue("symbol", t.getSymbol())
                .addValue("price", t.getPrice());
        // :createdTs / :createdBy are contributed automatically — see below.
    }
}
```

That is enough for `insert`, `insertBatch` and `insertAndReturnKey` to work with automatic
`created_ts` / `created_by` stamping.

---

## Configuration

All properties live under the `app.database` prefix (`DbProperties`):

| Property | Default | Meaning |
|---|---|---|
| `app.database.url` | — (required to create a pooled DataSource) | JDBC URL |
| `app.database.username` | — (required) | DB user |
| `app.database.password` | — | DB password (optional, e.g. H2) |
| `app.database.driver-class-name` | — | JDBC driver class |
| `app.database.max-pool-size` | `10` | Hikari maximum pool size |
| `app.database.min-pool-size` | `2` | Hikari minimum idle |
| `app.database.query-timeout` | `30` | **JDBC statement** timeout, in **seconds** |
| `app.database.connection-timeout` | `30` | **Pool acquisition** wait, in **seconds** (applied to Hikari in ms) |
| `app.database.fetch-size` | `1000` | JDBC fetch size |

`query-timeout` and `connection-timeout` are deliberately separate concerns — one bounds how long a
statement may run, the other how long you'll wait for a free connection.

Every auto‑configured bean is `@ConditionalOnMissingBean`, so you can override any of them by
declaring your own.

---

## The entity model

### `BaseEntity<ID>`

The only mandatory base: `ID getId()` / `void setId(ID)`. `ID` can be anything — `Long`, `UUID`, or a
record for a [composite key](#composite-keys).

### Capability mixins

| Interface | Methods | Grants |
|---|---|---|
| `CreateAudit` | `getCreatedTs/setCreatedTs`, `getCreatedBy/setCreatedBy` | create‑audit stamping |
| `UpdateAudit` | `getUpdatedTs/setUpdatedTs`, `getUpdatedBy/setUpdatedBy` | update‑audit stamping |
| `Auditable` | *(extends `CreateAudit` + `UpdateAudit`)* | full audit |
| `SoftDeletable` | `isActive/setActive` | soft delete |
| `Versionable` | `getVersion/setVersion` | optimistic locking |

An entity implements exactly the mixins its table supports. The repository base class checks
`instanceof` on each and contributes behavior accordingly.

### Pre‑composed conveniences

| Class | Composition | Typical use |
|---|---|---|
| `AuditedEntity<ID>` | `Auditable` | audited records created then updated (process logs, registrations) |
| `DomainEntity<ID>` | `Auditable` + `SoftDeletable` | mutable, audited, soft‑deletable records |

These two carry the audit (and, for `DomainEntity`, `active`) *fields* so you don't retype them. For any
other shape — append‑only create‑audit only, versioned, a composite key, no audit at all — extend
`BaseEntity<ID>` directly and implement just the mixins you need. No core change is ever required; the
repository dispatches on the interfaces, not on these classes.

### Supplying the auditor and clock

Audit stamping reads two beans (both have sensible defaults):

```java
public interface AuditorProvider { String currentAuditor(); }   // default bean returns "system"
// java.time.Clock                                               // default bean = Clock.systemUTC()
```

A web app binds `AuditorProvider` to the authenticated principal; a batch job binds it to a job name.
Declare your own bean to override the default.

---

## Writing a repository

Extend `AbstractJdbcRepository<T, ID>`. Only two methods are mandatory:

```java
protected abstract String getTableName();
protected abstract RowMapper<T> getRowMapper();
```

Everything else is an **opt‑in hook** that throws `UnsupportedOperationException` until you override
it — so a read‑only projection repository implements only the finders, and a write‑only loader
implements only the insert hooks. See the [Extension hooks reference](#extension-hooks-reference).

Four constructors are available — pass whichever template your context has; they resolve to the same
backing template either way:

```java
AbstractJdbcRepository(NamedParameterJdbcTemplate template);                 // Clock.systemUTC(), auditor "system"
AbstractJdbcRepository(JdbcTemplate template);                               // wrapped in a NamedParameterJdbcTemplate
AbstractJdbcRepository(NamedParameterJdbcTemplate template, Clock clock, AuditorProvider auditorProvider);
AbstractJdbcRepository(JdbcTemplate template, Clock clock, AuditorProvider auditorProvider);
```

### Two templates, one backing instance

CRUD and the named‑param helpers run on `NamedParameterJdbcTemplate`. For the cases where named
parameters are not the right tool — **positional‑parameter SQL (`?`)**, `queryForStream`, or building a
**`SimpleJdbcCall` for stored procedures** — the base class also exposes the underlying template:

```java
protected final NamedParameterJdbcTemplate namedParameterJdbcTemplate;  // ':name' params — CRUD, helpers
protected final JdbcTemplate jdbcTemplate;                              // '?' params, SimpleJdbcCall, streaming
```

They are **the same backing template** (`namedParameterJdbcTemplate.getJdbcTemplate()`), so there is no
second connection or configuration to keep in sync — a `NamedParameterJdbcTemplate` simply delegates to
this `JdbcTemplate`. See the [stored‑procedure example](./README_each_use_case.md#16-calling-a-stored-procedure-simplejdbccall).

---

## Reserved parameter names (automatic audit)

When an entity implements a capability mixin, the base class **adds these named parameters for you**
to every insert/update. Your SQL references them; your `getInsertParams` / `getUpdateParams` must
**not** set them (they're overwritten):

| Parameter | Added when the entity is… | On insert | On update |
|---|---|---|---|
| `:createdTs` | `CreateAudit` | ✅ | — |
| `:createdBy` | `CreateAudit` | ✅ | — |
| `:updatedTs` | `UpdateAudit` | ✅ | ✅ |
| `:updatedBy` | `UpdateAudit` | ✅ | ✅ |
| `:active` | `SoftDeletable` | ✅ | ✅ |
| `:version` | `Versionable` | ✅ | ✅ |

Values come from the entity after `beforeInsert` / `beforeUpdate` run:

- `beforeInsert` sets `createdTs`/`createdBy` **only if null** (so a supplied value is respected), and
  always sets `updatedTs`/`updatedBy`.
- `beforeUpdate` always sets `updatedTs`/`updatedBy`.

So a repository for a fully‑audited, versioned, soft‑deletable table writes:

```java
@Override protected String getInsertSql() {
    return "INSERT INTO event (name, created_ts, created_by, updated_ts, updated_by, active, version) " +
           "VALUES (:name, :createdTs, :createdBy, :updatedTs, :updatedBy, :active, :version)";
}
@Override protected MapSqlParameterSource getInsertParams(Event e) {
    return new MapSqlParameterSource("name", e.getName());   // only the business columns
}

@Override protected String getUpdateSql() {
    return "UPDATE event SET name=:name, updated_ts=:updatedTs, updated_by=:updatedBy, version=version+1 " +
           "WHERE id=:id AND version=:version";              // optimistic lock predicate
}
@Override protected MapSqlParameterSource getUpdateParams(Event e) {
    return new MapSqlParameterSource().addValue("name", e.getName()).addValue("id", e.getId());
}
```

---

## Repository API reference

These are the public operations on `BaseEntityRepository<T, ID>` (implemented by
`AbstractJdbcRepository`). Read operations run `@Transactional(readOnly = true)`, writes run
`@Transactional` (joining the caller's transaction when there is one).

| Method | Returns | Notes |
|---|---|---|
| `findById(ID id)` | `Optional<T>` | empty when not found |
| `findAll()` | `List<T>` | **unbounded** — for bounded reads use the paged overload |
| `findAll(PageRequest page)` | `PageResult<T>` | appends `LIMIT/OFFSET`; `total` via `count()` |
| `count()` | `long` | |
| `insert(T entity)` | `Integer` | always `1`, else throws; stamps create/update audit |
| `insertAndReturnKey(T entity)` | `ID` | returns the DB‑generated key **and** sets it on `entity` |
| `insertBatch(List<T> entities)` | `int[]` | one JDBC batch; audit stamped per row; `[]` for empty input |
| `upsertBatch(List<T> entities)` | `int[]` | same, using `getUpsertSql()` |
| `update(T entity)` | `Integer` | id taken from `entity.getId()`; see zero‑row semantics below |
| `deleteById(ID id)` | `Integer` | rows affected; **soft** when `isSoftDelete()` is `true` |
| `deleteAll()` | `int` | rows affected |

**Ad‑hoc query helpers** (use these for any query beyond CRUD — they apply the same error
translation):

| Method | Returns | Notes |
|---|---|---|
| `queryOptional(sql, params)` | `Optional<T>` | single row via the entity row mapper |
| `queryList(sql, params)` | `List<T>` | multiple rows via the entity row mapper |
| `queryList(sql, params, RowMapper<R>)` | `List<R>` | **projection** — joins / read models that are not `T` |
| `queryScalar(sql, params, Class<R>)` | `Optional<R>` | a single scalar (count, id, …) |
| `execUpdate(sql, params)` | `int` | any INSERT/UPDATE/DELETE |

`PageRequest(int limit, int offset)` validates `1 ≤ limit ≤ 1000` and `offset ≥ 0`.
`PageResult<T>(List<T> content, int limit, int offset, long total)`.

### `update(T)` zero‑row semantics

If the update affects **0 rows**:

- the entity is `Versionable` → throws `CoreJdbcException(OPTIMISTIC_LOCK)` (row gone *or* version moved);
- otherwise → throws `CoreJdbcException(NOT_FOUND)`.

On success, a `Versionable` entity's in‑memory `version` is incremented so the same instance can be
updated again.

---

## Extension hooks reference

Override only what your table needs. Unless noted, the default throws
`UnsupportedOperationException`.

| Hook | Default | Purpose |
|---|---|---|
| `getTableName()` | **abstract** | table name (used in SQL + error messages) |
| `getRowMapper()` | **abstract** | maps a row to `T` |
| `getInsertSql()` / `getInsertParams(T)` | unsupported | INSERT |
| `getUpsertSql()` | unsupported | INSERT … ON CONFLICT / MERGE for `upsertBatch` |
| `getUpdateSql()` / `getUpdateParams(T)` | unsupported | UPDATE |
| `getFindByIdSql()` / `getIdParam(ID)` | unsupported | single‑row read / id binding |
| `getFindAllSql()` | unsupported | list + base for paging |
| `getCountSql()` | unsupported | `count()` and page totals |
| `getDeleteByIdSql()` / `getDeleteAllSql()` | unsupported | hard delete |
| `getGeneratedKeyColumns()` | `{"id"}` | key columns for `insertAndReturnKey` |
| `extractKey(KeyHolder)` | first/`id` key value | map generated keys to `ID` (override for composite) |
| `isSoftDelete()` | `false` | when `true`, `deleteById` soft‑deletes |
| `getSoftDeleteSql()` | `UPDATE <table> SET active=false, updated_ts=:updatedTs, updated_by=:updatedBy WHERE id=:id` | soft delete statement |
| `activePredicate()` | `" AND active = true"` | fragment to compose into finders so they skip inactive rows |
| `beforeInsert(T)` / `beforeUpdate(T)` | audit stamping | override to add your own pre‑write logic (call `super`) |

**Soft delete** is explicit by design: the base class never rewrites your finder SQL. A soft‑delete
repository composes `activePredicate()` into its own queries:

```java
List<Event> findUpcoming() {
    return queryList("SELECT * FROM event WHERE starts_at > now()" + activePredicate() + " ORDER BY starts_at",
                     params());
}
```

### Generated keys

- **Recommended (portable, audit‑aware):** `insertAndReturnKey(entity)` inserts and reads the
  database‑generated key back via a `KeyHolder`. It runs the full built‑in insert path, so the audit
  parameters are contributed automatically and the key is set back on the entity. Works on H2 (tests)
  and Postgres/MySQL/SQL Server/Oracle.
- **Postgres `RETURNING` (manual):** you can run `INSERT … RETURNING id` yourself via `queryScalar`.
  Call `beforeInsert(entity)` to stamp the audit fields, then reuse the protected `insertParams(entity)`
  wrapper — it contributes the reserved audit / soft‑delete / version parameters, so you don't name them:

  ```java
  public Long insertReturning(Event e) {
      beforeInsert(e);
      return queryScalar(getInsertSql() + " RETURNING id", insertParams(e), Long.class).orElseThrow();
  }
  ```

  `insertParams(T)` and its counterpart `updateParams(T)` are `protected`, so a subclass can reuse the
  same audit contribution for any hand‑written insert/update SQL.

---

## Error handling

Every operation funnels Spring's `DataAccessException` hierarchy through `translate(…)` into a single
`CoreJdbcException` carrying an `ErrorCode`:

| `ErrorCode` | Raised for |
|---|---|
| `DUPLICATE_KEY` | `DuplicateKeyException` |
| `OPTIMISTIC_LOCK` | `OptimisticLockingFailureException`, or a zero‑row versioned `update` |
| `CONSTRAINT_VIOLATION` | `DataIntegrityViolationException` |
| `NOT_FOUND` | a zero‑row non‑versioned `update` |
| `QUERY_FAILURE` | anything else (also logged) |
| `CONNECTION_FAILURE`, `CONFIGURATION_ERROR`, `MAPPING_FAILURE`, `TRANSACTION_FAILURE` | lower‑level faults |

The catch is narrowed to `DataAccessException` on purpose: genuine programming errors (a null row
mapper, a bad cast) propagate as themselves instead of being relabeled a query failure.

**Keep the mapping in the library, the HTTP status in the API.** `core-jdbc` does not depend on Spring
MVC. A web app translates `ErrorCode` to a status in one place:

```java
@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(CoreJdbcException.class)
    ResponseEntity<Object> handle(CoreJdbcException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;                                   // 404
            case DUPLICATE_KEY, CONSTRAINT_VIOLATION, OPTIMISTIC_LOCK -> HttpStatus.CONFLICT; // 409
            default -> HttpStatus.INTERNAL_SERVER_ERROR;                              // 500
        };
        return ResponseEntity.status(status)
                .body(Map.of("error", ex.getErrorCode().name(), "message", ex.getMessage()));
    }
}
```

---

## Health checks

`DbHealthService` is auto‑registered and offers:

```java
boolean isHealthy();                 // SELECT 1
String  getDatabaseInfo();           // product name + version
boolean testConnection(int seconds); // probe with a per-statement timeout (never mutates the shared template)
```

If (and only if) Spring Boot **actuator** is on the classpath, a `DbHealthIndicator` is also
registered (`@ConditionalOnClass(HealthIndicator.class)`), surfacing database status on the
`/actuator/health` endpoint. Projects without actuator pay nothing for it.

---

## Composite keys

`BaseEntity<ID>` accepts a record as its `ID`. Decompose it into named parameters in `getIdParam`, and
override `extractKey` if the key is database‑generated:

```java
public record EventJoinerId(UUID eventId, UUID attendeeId) {}

public class EventJoiner extends BaseEntity<EventJoinerId> implements CreateAudit {
    private EventJoinerId id;
    @Override public EventJoinerId getId() { return id; }
    @Override public void setId(EventJoinerId id) { this.id = id; }
    // + createdTs / createdBy …
}

public class EventJoinerRepository extends AbstractJdbcRepository<EventJoiner, EventJoinerId> {
    public EventJoinerRepository(NamedParameterJdbcTemplate t) { super(t); }
    @Override protected String getTableName() { return "event_joiner"; }
    @Override protected String getFindByIdSql() {
        return "SELECT * FROM event_joiner WHERE event_id=:eventId AND attendee_id=:attendeeId";
    }
    @Override protected MapSqlParameterSource getIdParam(EventJoinerId id) {
        return new MapSqlParameterSource()
                .addValue("eventId", id.eventId())
                .addValue("attendeeId", id.attendeeId());
    }
    // getRowMapper, getInsertSql, getInsertParams …
}
```

The same shape covers auction *bid‑per‑item‑per‑bidder* and restaurant *table‑per‑slot*.

---

## Beans provided by auto‑configuration

Registered by `DbConfig` (via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`).
All are `@ConditionalOnMissingBean`:

| Bean | Condition | Notes |
|---|---|---|
| `HikariDataSource` | `app.database.url` set, no existing `DataSource` | connection pool |
| `JdbcTemplate` | — | query timeout + fetch size applied |
| `NamedParameterJdbcTemplate` | — | what repositories receive |
| `PlatformTransactionManager` | — | `DataSourceTransactionManager` |
| `AuditorProvider` | — | default returns `"system"` |
| `Clock` | — | default `Clock.systemUTC()` |
| `DbHealthService` | — | health probes |
| `DbHealthIndicator` | actuator on classpath | `/actuator/health` contributor |

---

## License / attribution

Internal gothamdude library. See `pom.xml` for coordinates and dependency versions.
