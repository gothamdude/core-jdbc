package com.gothamdude.core.jdbc.model;

/**
 * Interface for entity objects.
 * User Management
 * 33
 *
 * @param <T> the type of {@link EntityId} that will be used in this entity
 */
public interface Entity<T extends EntityId> {

    T getId();

}