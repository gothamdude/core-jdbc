package com.gothamdude.core.jdbc.repository;

import com.gothamdude.core.jdbc.model.BaseEntity;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.Optional;

public interface BaseEntityRepository<T extends BaseEntity<ID>, ID> {

    Optional<T> findById(ID id);
    List<T> findAll();
    PageResult<T> findAll(PageRequest page);
    long count();
    Integer insert(T entity);
    ID insertAndReturnKey(T entity);
    int[] insertBatch(List<T> entities);
    int[] upsertBatch(List<T> entities);
    Integer update(T entity);
    Integer deleteById(ID id);
    int deleteAll();

    Optional<T> queryOptional(String sql, SqlParameterSource params);
    List<T> queryList(String sql, SqlParameterSource params);

    // Projection overload — joins and read models that are not T.
    // The event API needs this for speaker and attendee views.
    <R> List<R> queryList(String sql, SqlParameterSource params, RowMapper<R> mapper);
    <R> Optional<R> queryScalar(String sql, SqlParameterSource params, Class<R> type);
    int execUpdate(String sql, SqlParameterSource params);

}
