package com.gothamdude.core.jdbc.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

public abstract class TransactionEntity extends BaseEntity<Long> {

    @Getter
    @Setter
    private Long Id;

    @Getter
    @Setter
    private Instant createdTs;

    @Getter
    @Setter
    private String createdBy;

}
