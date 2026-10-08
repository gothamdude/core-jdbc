package com.gothamdude.core.jdbc.repository;

import com.gothamdude.core.jdbc.model.BaseEntity;

import java.util.List;
import java.util.Optional;

public interface BaseEntityRepository<T extends BaseEntity<ID>,ID> {
    Optional<T> findById(ID id);
    List<T> findAll();
    Integer count();
    Integer insert(T entity);
    Integer update(T entity, ID id);
    Integer deleteById(ID id);
    void deleteAll();
}
