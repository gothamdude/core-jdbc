package com.gothamdude.core.jdbc.model;

public interface SoftDeletable {
    boolean isActive();
    void setActive(boolean active);
}
