package com.gothamdude.core.jdbc.model;

import java.time.Instant;

public interface CreateAudit {
    Instant getCreatedTs();
    void setCreatedTs(Instant ts);
    String getCreatedBy();
    void setCreatedBy(String user);
}
