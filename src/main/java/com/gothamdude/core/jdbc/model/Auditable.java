package com.gothamdude.core.jdbc.model;

import java.time.Instant;
import java.time.LocalDateTime;

public interface Auditable {

    Instant getCreatedTs();

    String getCreatedBy();

    Instant getUpdatedTs();

    String getUpdatedBy();

    void setCreatedTs(Instant ts);

    void setCreatedBy(String user);

    void setUpdatedTs(Instant ts);

    void setUpdatedBy(String user);

}
