package com.gothamdude.core.jdbc.model;

import java.time.Instant;
import java.time.LocalDateTime;

public interface Auditable {

    Instant getCreatedTs();

    void setCreatedTs(Instant ts);

    String getCreatedBy();

    void setCreatedBy(String user);

    Instant getLastUpdatedTs();

    void setLastUpdatedTs(Instant ts);

    String getLastUpdatedBy();

    void setLastUpdatedBy(String user);

}
