package com.gothamdude.core.jdbc.repository;

// Deliberately not Spring Data's Pageable — keep the library free of that dependency.
public record PageRequest(int limit, int offset) {
    public PageRequest {
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be 1..1000");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
    }
}
