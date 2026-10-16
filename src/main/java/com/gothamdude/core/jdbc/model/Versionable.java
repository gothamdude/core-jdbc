package com.gothamdude.core.jdbc.model;

public interface Versionable {
    long getVersion();
    void setVersion(long version);
}
