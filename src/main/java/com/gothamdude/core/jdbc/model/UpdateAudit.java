package com.gothamdude.core.jdbc.model;

import java.time.Instant;

public interface UpdateAudit {
    Instant getUpdatedTs();
    void setUpdatedTs(Instant ts);
    String getUpdatedBy();
    void setUpdatedBy(String user);
}
