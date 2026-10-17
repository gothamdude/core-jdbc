package com.gothamdude.core.jdbc.model;

import java.time.Instant;

/**
 * Convenience base for audited records that are created and later updated but are
 * <em>not</em> soft-deletable — e.g. process logs or event registrations. Carries the
 * create and update audit fields so a subclass adds only its own id and business columns.
 *
 * <ul>
 *   <li>Need audit + soft delete? Use {@link DomainEntity}.</li>
 *   <li>Need a different mix (create-audit only, versioned, a composite key, …)?
 *       Extend {@link BaseEntity} directly and implement the capability interfaces you want.</li>
 * </ul>
 */
public abstract class AuditedEntity<ID> extends BaseEntity<ID> implements Auditable {

    private Instant createdTs;
    private String createdBy;
    private Instant updatedTs;
    private String updatedBy;

    @Override
    public Instant getCreatedTs() {
        return createdTs;
    }

    @Override
    public void setCreatedTs(Instant ts) {
        this.createdTs = ts;
    }

    @Override
    public String getCreatedBy() {
        return createdBy;
    }

    @Override
    public void setCreatedBy(String user) {
        this.createdBy = user;
    }

    @Override
    public Instant getUpdatedTs() {
        return updatedTs;
    }

    @Override
    public void setUpdatedTs(Instant ts) {
        this.updatedTs = ts;
    }

    @Override
    public String getUpdatedBy() {
        return updatedBy;
    }

    @Override
    public void setUpdatedBy(String user) {
        this.updatedBy = user;
    }
}
