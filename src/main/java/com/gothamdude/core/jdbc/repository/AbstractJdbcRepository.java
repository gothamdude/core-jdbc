package com.gothamdude.core.jdbc.repository;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import com.gothamdude.core.jdbc.model.AuditorProvider;
import com.gothamdude.core.jdbc.model.BaseEntity;
import com.gothamdude.core.jdbc.model.CreateAudit;
import com.gothamdude.core.jdbc.model.SoftDeletable;
import com.gothamdude.core.jdbc.model.UpdateAudit;
import com.gothamdude.core.jdbc.model.Versionable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.gothamdude.core.jdbc.exception.CoreJdbcException.ErrorCode;

@Slf4j
public abstract class AbstractJdbcRepository<T extends BaseEntity<ID>, ID> implements BaseEntityRepository<T, ID> {

    protected final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    // The underlying JdbcTemplate, exposed so subclasses can use positional-parameter
    // operations and build a SimpleJdbcCall for stored procedures. CRUD still runs on
    // the NamedParameterJdbcTemplate above. These are the same backing template: a
    // NamedParameterJdbcTemplate delegates to this JdbcTemplate.
    protected final JdbcTemplate jdbcTemplate;
    protected final Clock clock;
    protected final AuditorProvider auditorProvider;

    protected AbstractJdbcRepository(NamedParameterJdbcTemplate template) {
        this(template, Clock.systemUTC(), () -> "system");
    }

    protected AbstractJdbcRepository(JdbcTemplate template) {
        this(new NamedParameterJdbcTemplate(template));
    }

    protected AbstractJdbcRepository(NamedParameterJdbcTemplate template,
                                     Clock clock,
                                     AuditorProvider auditorProvider) {
        this.namedParameterJdbcTemplate = template;
        this.jdbcTemplate = template.getJdbcTemplate();
        this.clock = clock;
        this.auditorProvider = auditorProvider;
    }

    protected AbstractJdbcRepository(JdbcTemplate template,
                                     Clock clock,
                                     AuditorProvider auditorProvider) {
        this(new NamedParameterJdbcTemplate(template), clock, auditorProvider);
    }

    // ------------------------------------------------------------------
    // SQL / mapping hooks — only getTableName() and getRowMapper() are
    // mandatory; everything else defaults to unsupported so a repository
    // opts into just the operations it needs.
    // ------------------------------------------------------------------

    protected abstract String getTableName();

    protected abstract RowMapper<T> getRowMapper();

    protected String getInsertSql() {
        throw new UnsupportedOperationException("insert not supported by " + getTableName());
    }

    protected String getUpsertSql() {
        throw new UnsupportedOperationException("upsert not supported by " + getTableName());
    }

    protected String getUpdateSql() {
        throw new UnsupportedOperationException("update not supported by " + getTableName());
    }

    protected String getFindByIdSql() {
        throw new UnsupportedOperationException("findById not supported by " + getTableName());
    }

    protected String getFindAllSql() {
        throw new UnsupportedOperationException("findAll not supported by " + getTableName());
    }

    protected String getCountSql() {
        throw new UnsupportedOperationException("count not supported by " + getTableName());
    }

    protected String getDeleteByIdSql() {
        throw new UnsupportedOperationException("deleteById not supported by " + getTableName());
    }

    protected String getDeleteAllSql() {
        throw new UnsupportedOperationException("deleteAll not supported by " + getTableName());
    }

    protected MapSqlParameterSource getInsertParams(T entity) {
        throw new UnsupportedOperationException("insert not supported by " + getTableName());
    }

    protected MapSqlParameterSource getUpdateParams(T entity) {
        throw new UnsupportedOperationException("update not supported by " + getTableName());
    }

    protected MapSqlParameterSource getIdParam(ID id) {
        throw new UnsupportedOperationException("id param not supported by " + getTableName());
    }

    // The columns a database-generated-key insert should read back. Default
    // assumes a single key column named "id"; override where it differs.
    protected String[] getGeneratedKeyColumns() {
        return new String[]{"id"};
    }

    // ------------------------------------------------------------------
    // Soft delete — opt-in behavior rather than per-repository SQL.
    // ------------------------------------------------------------------

    protected boolean isSoftDelete() {
        return false;
    }

    protected String getSoftDeleteSql() {
        return "UPDATE " + getTableName()
                + " SET active = false, updated_ts = :updatedTs, updated_by = :updatedBy"
                + " WHERE id = :id";
    }

    // Fragment a soft-delete repository composes into its finder SQL so that
    // inactive rows are excluded. Explicit and greppable beats parsing SQL.
    protected String activePredicate() {
        return " AND active = true";
    }

    // ------------------------------------------------------------------
    // Lifecycle hooks — dispatch on the capability mixins, so audit data is
    // stamped once here instead of by every subclass.
    // ------------------------------------------------------------------

    protected void beforeInsert(T entity) {
        Instant now = clock.instant();
        String who = auditorProvider.currentAuditor();
        if (entity instanceof CreateAudit a) {
            if (a.getCreatedTs() == null) a.setCreatedTs(now);
            if (a.getCreatedBy() == null) a.setCreatedBy(who);
        }
        if (entity instanceof UpdateAudit a) {
            a.setUpdatedTs(now);
            a.setUpdatedBy(who);
        }
    }

    protected void beforeUpdate(T entity) {
        if (entity instanceof UpdateAudit a) {
            a.setUpdatedTs(clock.instant());
            a.setUpdatedBy(auditorProvider.currentAuditor());
        }
    }

    // Wrap the subclass params with the fixed audit / soft-delete / version
    // parameter names, so subclass SQL references :createdTs :createdBy
    // :updatedTs :updatedBy :active :version without naming them in code.
    // Protected so a subclass writing custom insert SQL (e.g. INSERT ... RETURNING)
    // can reuse the same contribution after calling beforeInsert(entity).
    protected SqlParameterSource insertParams(T entity) {
        MapSqlParameterSource p = getInsertParams(entity);
        if (entity instanceof CreateAudit a) {
            p.addValue("createdTs", a.getCreatedTs() == null ? null : Timestamp.from(a.getCreatedTs()));
            p.addValue("createdBy", a.getCreatedBy());
        }
        if (entity instanceof UpdateAudit a) {
            p.addValue("updatedTs", a.getUpdatedTs() == null ? null : Timestamp.from(a.getUpdatedTs()));
            p.addValue("updatedBy", a.getUpdatedBy());
        }
        if (entity instanceof SoftDeletable s) p.addValue("active", s.isActive());
        if (entity instanceof Versionable v) p.addValue("version", v.getVersion());
        return p;
    }

    // Protected counterpart to insertParams, reusable for custom update SQL
    // after calling beforeUpdate(entity).
    protected SqlParameterSource updateParams(T entity) {
        MapSqlParameterSource p = getUpdateParams(entity);
        if (entity instanceof UpdateAudit a) {
            p.addValue("updatedTs", a.getUpdatedTs() == null ? null : Timestamp.from(a.getUpdatedTs()));
            p.addValue("updatedBy", a.getUpdatedBy());
        }
        if (entity instanceof SoftDeletable s) p.addValue("active", s.isActive());
        if (entity instanceof Versionable v) p.addValue("version", v.getVersion());
        return p;
    }

    // ------------------------------------------------------------------
    // CRUD — all routed through the query helpers, so the error-handling
    // policy lives in exactly one place (translate()).
    // ------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        return queryOptional(getFindByIdSql(), getIdParam(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findAll() {
        return queryList(getFindAllSql(), new MapSqlParameterSource());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<T> findAll(PageRequest page) {
        List<T> rows = queryList(getFindAllSql() + " LIMIT :limit OFFSET :offset",
                params("limit", page.limit(), "offset", page.offset()));
        return new PageResult<>(rows, page.limit(), page.offset(), count());
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return queryScalar(getCountSql(), new MapSqlParameterSource(), Long.class).orElse(0L);
    }

    @Override
    @Transactional
    public Integer insert(T entity) {
        beforeInsert(entity);
        int rowsAffected = execUpdate(getInsertSql(), insertParams(entity));
        if (rowsAffected != 1) {
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE,
                    "Insert affected " + rowsAffected + " rows, expected 1 on " + getTableName());
        }
        log.debug("Inserted entity into table {}", getTableName());
        return rowsAffected;
    }

    @Override
    @Transactional
    public ID insertAndReturnKey(T entity) {
        beforeInsert(entity);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        try {
            namedParameterJdbcTemplate.update(
                    getInsertSql(), insertParams(entity), keyHolder, getGeneratedKeyColumns());
        } catch (DataAccessException e) {
            throw translate(e, "Insert failed on " + getTableName());
        }
        ID id = extractKey(keyHolder);
        entity.setId(id);
        return id;
    }

    // Default assumes the first key column maps straight to ID. Override
    // where it does not (e.g. a composite or non-"id" key column).
    @SuppressWarnings("unchecked")
    protected ID extractKey(KeyHolder keyHolder) {
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE,
                    "No generated key returned by " + getTableName());
        }
        // Single-column key: take it by value, so a driver that cases the
        // column differently (H2 returns "ID") still resolves. Fall back to
        // the named column only for a multi-column key.
        Object value = keys.size() == 1
                ? keys.values().iterator().next()
                : keys.get(getGeneratedKeyColumns()[0]);
        return (ID) value;
    }

    @Override
    @Transactional
    public int[] insertBatch(List<T> entities) {
        if (entities.isEmpty()) return new int[0];
        entities.forEach(this::beforeInsert);
        SqlParameterSource[] batch = entities.stream()
                .map(this::insertParams)
                .toArray(SqlParameterSource[]::new);
        try {
            return namedParameterJdbcTemplate.batchUpdate(getInsertSql(), batch);
        } catch (DataAccessException e) {
            throw translate(e, "Batch insert failed on " + getTableName());
        }
    }

    @Override
    @Transactional
    public int[] upsertBatch(List<T> entities) {
        if (entities.isEmpty()) return new int[0];
        entities.forEach(this::beforeInsert);
        SqlParameterSource[] batch = entities.stream()
                .map(this::insertParams)
                .toArray(SqlParameterSource[]::new);
        try {
            return namedParameterJdbcTemplate.batchUpdate(getUpsertSql(), batch);
        } catch (DataAccessException e) {
            throw translate(e, "Batch upsert failed on " + getTableName());
        }
    }

    @Override
    @Transactional
    public Integer update(T entity) {
        beforeUpdate(entity);
        int rows = execUpdate(getUpdateSql(), updateParams(entity));
        if (rows == 0) {
            if (entity instanceof Versionable) {
                // Either the row is gone or someone else bumped the version.
                throw new CoreJdbcException(ErrorCode.OPTIMISTIC_LOCK,
                        "Stale version for id " + entity.getId() + " in " + getTableName());
            }
            throw new CoreJdbcException(ErrorCode.NOT_FOUND,
                    "No row with id " + entity.getId() + " in " + getTableName());
        }
        if (entity instanceof Versionable v) v.setVersion(v.getVersion() + 1);
        log.debug("Updated entity with id {} in table {}", entity.getId(), getTableName());
        return rows;
    }

    @Override
    @Transactional
    public Integer deleteById(ID id) {
        if (isSoftDelete()) {
            MapSqlParameterSource p = getIdParam(id);
            p.addValue("updatedTs", Timestamp.from(clock.instant()));
            p.addValue("updatedBy", auditorProvider.currentAuditor());
            return execUpdate(getSoftDeleteSql(), p);
        }
        return execUpdate(getDeleteByIdSql(), getIdParam(id));
    }

    @Override
    @Transactional
    public int deleteAll() {
        return execUpdate(getDeleteAllSql(), new MapSqlParameterSource());
    }

    // ------------------------------------------------------------------
    // Query helpers — the single home of the try / catch / translate
    // plumbing, so any custom query gets the same error handling as CRUD.
    // ------------------------------------------------------------------

    @Override
    public Optional<T> queryOptional(String sql, SqlParameterSource params) {
        try {
            return Optional.ofNullable(
                    namedParameterJdbcTemplate.queryForObject(sql, params, getRowMapper()));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (DataAccessException e) {
            throw translate(e, "Query failed on " + getTableName());
        }
    }

    @Override
    public List<T> queryList(String sql, SqlParameterSource params) {
        return queryList(sql, params, getRowMapper());
    }

    // Projection overload — joins and read models that are not T.
    // The event API needs this for speaker/attendee views.
    @Override
    public <R> List<R> queryList(String sql, SqlParameterSource params, RowMapper<R> mapper) {
        try {
            return namedParameterJdbcTemplate.query(sql, params, mapper);
        } catch (DataAccessException e) {
            throw translate(e, "Query failed on " + getTableName());
        }
    }

    @Override
    public <R> Optional<R> queryScalar(String sql, SqlParameterSource params, Class<R> type) {
        try {
            return Optional.ofNullable(
                    namedParameterJdbcTemplate.queryForObject(sql, params, type));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (DataAccessException e) {
            throw translate(e, "Scalar query failed on " + getTableName());
        }
    }

    @Override
    public int execUpdate(String sql, SqlParameterSource params) {
        try {
            return namedParameterJdbcTemplate.update(sql, params);
        } catch (DataAccessException e) {
            throw translate(e, "Update failed on " + getTableName());
        }
    }

    // Small ergonomic win: params("eventId", id, "from", from, "to", to)
    protected MapSqlParameterSource params(Object... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("params() needs key/value pairs");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        for (int i = 0; i < kv.length; i += 2) {
            p.addValue((String) kv[i], kv[i + 1]);
        }
        return p;
    }

    // Map Spring's classified DataAccessException hierarchy onto ErrorCode,
    // so callers (an @ControllerAdvice, an importer's reject router) can act
    // on the cause instead of a single flat QUERY_FAILURE.
    protected CoreJdbcException translate(DataAccessException e, String message) {
        // Order matters: DuplicateKeyException extends
        // DataIntegrityViolationException, so it must be tested first.
        if (e instanceof DuplicateKeyException) {
            return new CoreJdbcException(ErrorCode.DUPLICATE_KEY, message, e);
        }
        if (e instanceof OptimisticLockingFailureException) {
            return new CoreJdbcException(ErrorCode.OPTIMISTIC_LOCK, message, e);
        }
        if (e instanceof DataIntegrityViolationException) {
            return new CoreJdbcException(ErrorCode.CONSTRAINT_VIOLATION, message, e);
        }
        log.error("{} : {}", message, e.getMessage(), e);
        return new CoreJdbcException(ErrorCode.QUERY_FAILURE, message, e);
    }

}
