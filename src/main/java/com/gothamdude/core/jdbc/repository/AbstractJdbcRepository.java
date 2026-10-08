package com.gothamdude.core.jdbc.repository;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import com.gothamdude.core.jdbc.model.BaseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.gothamdude.core.jdbc.exception.CoreJdbcException.ErrorCode;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractJdbcRepository<T extends BaseEntity<ID>, ID> implements BaseEntityRepository<T, ID> {

    protected final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    // Only truly universal abstracts — every repo needs these
    protected abstract String getTableName();

    protected abstract RowMapper<T> getRowMapper();

    // Everything else defaults to unsupported — override only when needed
    protected String getInsertSql() {
        throw new UnsupportedOperationException("insert not supported by " + getTableName());
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

    protected MapSqlParameterSource getUpdateParams(T entity, ID id) {
        throw new UnsupportedOperationException("update not supported by " + getTableName());
    }

    protected MapSqlParameterSource getIdParam(ID id) {
        throw new UnsupportedOperationException("id param not supported by " + getTableName());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<T> findById(ID id) {
        try {
            T result = namedParameterJdbcTemplate.queryForObject(
                    getFindByIdSql(), getIdParam(id), getRowMapper()
            );
            return Optional.ofNullable(result);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        } catch (Exception e) {
            log.error("Error finding entity by ID {} in table {}: {}", id, getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to find entity by ID", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<T> findAll() {
        try {
            return namedParameterJdbcTemplate.query(
                    getFindAllSql(), new MapSqlParameterSource(), getRowMapper()
            );
        } catch (Exception e) {
            log.error("Error finding all entities in table {}: {}", getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to find all entities", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Integer count() {
        try {
            Integer count = namedParameterJdbcTemplate.queryForObject(
                    getCountSql(), new MapSqlParameterSource(), Integer.class
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error counting entities in table {}: {}", getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to count entities", e);
        }
    }

    @Override
    @Transactional
    public Integer insert(T entity) {
        try {
            int rowsAffected = namedParameterJdbcTemplate.update(getInsertSql(), getInsertParams(entity));
            if (rowsAffected != 1) {
                throw new CoreJdbcException(ErrorCode.QUERY_FAILURE,
                        "Insert operation affected " + rowsAffected + " rows, expected 1");
            }
            log.debug("Inserted entity into table {}", getTableName());
            return rowsAffected;
        } catch (CoreJdbcException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error inserting entity into table {}: {}", getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to insert entity", e);
        }
    }

    @Override
    @Transactional
    public Integer update(T entity, ID id) {
        try {
            int rowsAffected = namedParameterJdbcTemplate.update(getUpdateSql(), getUpdateParams(entity, id));
            if (rowsAffected != 1) {
                throw new CoreJdbcException(ErrorCode.QUERY_FAILURE,
                        "Update operation affected " + rowsAffected + " rows, expected 1");
            }
            log.debug("Updated entity with ID {} in table {}", id, getTableName());
            return rowsAffected;
        } catch (CoreJdbcException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error updating entity with ID {} in table {}: {}", id, getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to update entity", e);
        }
    }

    @Override
    @Transactional
    public Integer deleteById(ID id) {
        try {
            int rowsAffected = namedParameterJdbcTemplate.update(getDeleteByIdSql(), getIdParam(id));
            if (rowsAffected == 0) {
                log.warn("No entity found with ID {} in table {}", id, getTableName());
            }
            return rowsAffected;
        } catch (Exception e) {
            log.error("Error deleting entity with ID {} in table {}: {}", id, getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to delete entity by ID", e);
        }
    }

    @Override
    @Transactional
    public void deleteAll() {
        try {
            int rowsAffected = namedParameterJdbcTemplate.update(getDeleteAllSql(), new MapSqlParameterSource());
            if (rowsAffected == 0) {
                log.warn("No entities deleted in table {}", getTableName());
            }
        } catch (Exception e) {
            log.error("Error deleting all entities in table {}: {}", getTableName(), e.getMessage(), e);
            throw new CoreJdbcException(ErrorCode.QUERY_FAILURE, "Failed to delete all entities", e);
        }
    }


}
