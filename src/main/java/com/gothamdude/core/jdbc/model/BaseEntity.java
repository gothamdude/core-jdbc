package com.gothamdude.core.jdbc.model;

public abstract class BaseEntity<ID> {

    public abstract ID getId();

    public abstract void setId(ID id);

}
