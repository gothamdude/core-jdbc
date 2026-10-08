package com.gothamdude.core.jdbc.model;

import java.time.Instant;

public abstract class DomainEntity<ID> extends BaseEntity<ID> implements Auditable, Activable {

    private Instant createdTs;
    private String createdBy;
    private Instant updatedTs;
    private String updatedBy;
    private boolean activeFlag = true;


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


    @Override
    public boolean getActiveFlag() {
        return activeFlag;
    }

    @Override
    public void setActiveFlag(boolean activeFlag) {
        this.activeFlag = activeFlag;
    }

}
