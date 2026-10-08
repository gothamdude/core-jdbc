package com.gothamdude.core.jdbc.repository;


import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import com.gothamdude.core.jdbc.model.BaseEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractJdbcRepositoryTest {

    // --- Minimal test entity ---
    static class TestEntity extends BaseEntity<UUID> {
        private UUID id;
        public UUID getId() { return id;}
        public void setId(UUID id) { this.id = id; }
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // --- Fully-implemented concrete repository for normal operation tests ---
    static class FullTestRepository extends AbstractJdbcRepository<TestEntity, UUID> {
        FullTestRepository(NamedParameterJdbcTemplate template) { super(template); }

        @Override protected String getTableName()       { return "test_table"; }
        @Override protected String getInsertSql()       { return "INSERT INTO test_table (id) VALUES (:id)"; }
        @Override protected String getUpdateSql()       { return "UPDATE test_table SET name=:name WHERE id=:id"; }
        @Override protected String getFindByIdSql()     { return "SELECT * FROM test_table WHERE id=:id"; }
        @Override protected String getFindAllSql()      { return "SELECT * FROM test_table"; }
        @Override protected String getCountSql()        { return "SELECT COUNT(*) FROM test_table"; }
        @Override protected String getDeleteByIdSql()   { return "DELETE FROM test_table WHERE id=:id"; }
        @Override protected String getDeleteAllSql()    { return "DELETE FROM test_table"; }
        @Override protected RowMapper<TestEntity> getRowMapper() { return (rs, rowNum) -> new TestEntity(); }

        @Override
        protected MapSqlParameterSource getInsertParams(TestEntity e) {
            return new MapSqlParameterSource("id", e.getId());
        }
        @Override
        protected MapSqlParameterSource getUpdateParams(TestEntity e, UUID id) {
            return new MapSqlParameterSource("id", id).addValue("name", e.getName());
        }
        @Override
        protected MapSqlParameterSource getIdParam(UUID id) {
            return new MapSqlParameterSource("id", id);
        }
    }

    // --- Minimal repository — only required abstracts, no SQL overrides ---
    static class MinimalRepository extends AbstractJdbcRepository<TestEntity, UUID> {
        MinimalRepository(NamedParameterJdbcTemplate template) { super(template); }
        @Override protected String getTableName() { return "minimal_table"; }
        @Override protected RowMapper<TestEntity> getRowMapper() { return (rs, rowNum) -> new TestEntity(); }
    }

    @Mock
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    private FullTestRepository repository;
    private TestEntity testEntity;
    private UUID testId;

    @BeforeEach
    void setUp() {
        repository = new FullTestRepository(namedParameterJdbcTemplate);
        testId = UUID.randomUUID();
        testEntity = new TestEntity();
        testEntity.setId(testId);
        testEntity.setName("Test Name");
    }

    // --- findById ---

    @Test
    void findByIdShouldReturnEntityWhenFound() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(testEntity);

        Optional<TestEntity> result = repository.findById(testId);

        assertThat(result).isPresent().contains(testEntity);
    }

    @Test
    void findByIdShouldReturnEmptyWhenEntityNotFound() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new EmptyResultDataAccessException(1));

        assertThat(repository.findById(testId)).isEmpty();
    }

    @Test
    void findByIdShouldReturnEmptyWhenRowMapperReturnsNull() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(null);

        assertThat(repository.findById(testId)).isEmpty();
    }

    @Test
    void findByIdShouldThrowCoreJdbcExceptionOnQueryError() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.findById(testId))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to find entity by ID");
    }

    // --- findAll ---

    @Test
    void findAllShouldReturnAllEntities() {
        List<TestEntity> expected = List.of(testEntity);
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(expected);

        assertThat(repository.findAll()).isEqualTo(expected);
    }

    @Test
    void findAllShouldReturnEmptyListWhenNoEntities() {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void findAllShouldThrowCoreJdbcExceptionOnQueryError() {
        when(namedParameterJdbcTemplate.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.findAll())
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to find all entities");
    }

    // --- count ---

    @Test
    void countShouldReturnRowCount() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(7);

        assertThat(repository.count()).isEqualTo(7);
    }

    @Test
    void countShouldReturnZeroWhenQueryReturnsNull() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenReturn(null);

        assertThat(repository.count()).isEqualTo(0);
    }

    @Test
    void countShouldThrowCoreJdbcExceptionOnError() {
        when(namedParameterJdbcTemplate.queryForObject(anyString(), any(MapSqlParameterSource.class), eq(Integer.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.count())
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to count entities");
    }

    // --- insert ---

    @Test
    void insertShouldReturnOneWhenSuccessful() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        assertThat(repository.insert(testEntity)).isEqualTo(1);
    }

    @Test
    void insertShouldThrowCoreJdbcExceptionWhenZeroRowsAffected() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.insert(testEntity))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Insert operation affected 0 rows");
    }

    @Test
    void insertShouldThrowCoreJdbcExceptionWhenMoreThanOneRowAffected() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(2);

        assertThatThrownBy(() -> repository.insert(testEntity))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Insert operation affected 2 rows");
    }

    @Test
    void insertShouldThrowCoreJdbcExceptionOnDbError() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.insert(testEntity))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to insert entity");
    }

    // --- update ---

    @Test
    void updateShouldReturnOneWhenSuccessful() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        assertThat(repository.update(testEntity, testId)).isEqualTo(1);
    }

    @Test
    void updateShouldThrowCoreJdbcExceptionWhenZeroRowsAffected() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);

        assertThatThrownBy(() -> repository.update(testEntity, testId))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Update operation affected 0 rows");
    }

    @Test
    void updateShouldThrowCoreJdbcExceptionWhenMoreThanOneRowAffected() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(3);

        assertThatThrownBy(() -> repository.update(testEntity, testId))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Update operation affected 3 rows");
    }

    @Test
    void updateShouldThrowCoreJdbcExceptionOnDbError() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.update(testEntity, testId))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to update entity");
    }

    // --- deleteById ---

    @Test
    void deleteByIdShouldReturnOneWhenEntityDeleted() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        assertThat(repository.deleteById(testId)).isEqualTo(1);
    }

    @Test
    void deleteByIdShouldReturnZeroWhenEntityNotFound() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        // no exception — just a warning log
        assertThat(repository.deleteById(testId)).isEqualTo(0);
    }

    @Test
    void deleteByIdShouldThrowCoreJdbcExceptionOnDbError() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.deleteById(testId))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to delete entity by ID");
    }

    // --- deleteAll ---

    @Test
    void deleteAllShouldCompleteWithoutExceptionWhenRowsDeleted() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(3);
        assertThatNoException().isThrownBy(() -> repository.deleteAll());
    }

    @Test
    void deleteAllShouldNotThrowWhenNoRowsDeleted() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        assertThatNoException().isThrownBy(() -> repository.deleteAll());
    }

    @Test
    void deleteAllShouldThrowCoreJdbcExceptionOnDbError() {
        when(namedParameterJdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new RuntimeException("DB error"));

        assertThatThrownBy(() -> repository.deleteAll())
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Failed to delete all entities");
    }

    // --- default unsupported operations on minimal repository ---

    @Test
    void getInsertSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getInsertSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getUpdateSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getUpdateSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getFindByIdSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getFindByIdSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getFindAllSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getFindAllSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getCountSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getCountSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getDeleteByIdSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getDeleteByIdSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void getDeleteAllSqlShouldThrowUnsupportedOperationByDefault() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getDeleteAllSql).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void unsupportedOperationMessageShouldContainTableName() {
        MinimalRepository minimal = new MinimalRepository(namedParameterJdbcTemplate);
        assertThatThrownBy(minimal::getInsertSql)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("minimal_table");
    }
}