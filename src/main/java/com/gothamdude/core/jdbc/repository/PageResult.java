package com.gothamdude.core.jdbc.repository;

import java.util.List;

public record PageResult<T>(List<T> content, int limit, int offset, long total) {
}
