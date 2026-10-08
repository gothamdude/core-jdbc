package com.gothamdude.core.jdbc.repository;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import com.gothamdude.core.jdbc.model.DomainEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for AbstractJdbcRepository using an H2 in-memory database.
 * Each test runs in a transaction that is rolled back, leaving a clean table for the next test.
 */
@SpringJUnitConfig(AbstractJdbcRepositoryIntegrationTest.TestConfig.class)
@Transactional
@Rollback
class AbstractJdbcRepositoryIntegrationTest {

    // --- Test entity ---

    static class UserEntity extends DomainEntity<UUID> {
        private UUID id;
        private String name;
        private String email;

        public UUID getId() { return id;}
        public void setId(UUID id) { this.id = id; }
        public String getName()              { return name; }
        public void setName(String name)     { this.name = name; }
        public String getEmail()             { return email; }
        public void setEmail(String email)   { this.email = email; }
    }

    // --- Concrete repository under test ---

    static class UserRepository extends AbstractJdbcRepository<UserEntity, UUID> {

        UserRepository(NamedParameterJdbcTemplate template) { super(template); }

        @Override protected String getTableName()     { return "users"; }
        @Override protected String getInsertSql()     {
            return "INSERT INTO users (id, name, email, is_active, created_by, created_at, updated_at, updated_by) " +
                   "VALUES (:id, :name, :email, :isActive, :createdBy, :createdAt, :updatedAt, :updatedBy)";
        }
        @Override protected String getUpdateSql()     { return "UPDATE users SET name=:name, email=:email WHERE id=:id"; }
        @Override protected String getFindByIdSql()   { return "SELECT * FROM users WHERE id=:id"; }
        @Override protected String getFindAllSql()    { return "SELECT * FROM users ORDER BY name"; }
        @Override protected String getCountSql()      { return "SELECT COUNT(*) FROM users"; }
        @Override protected String getDeleteByIdSql() { return "DELETE FROM users WHERE id=:id"; }
        @Override protected String getDeleteAllSql()  { return "DELETE FROM users"; }

        @Override
        protected RowMapper<UserEntity> getRowMapper() {
            return (rs, rowNum) -> {
                UserEntity e = new UserEntity();
                e.setId(UUID.fromString(rs.getString("id")));
                e.setName(rs.getString("name"));
                e.setEmail(rs.getString("email"));
                e.setActiveFlag(rs.getBoolean("is_active"));
                e.setCreatedBy(rs.getString("created_by"));
                return e;
            };
        }

        @Override
        protected MapSqlParameterSource getInsertParams(UserEntity e) {
            return new MapSqlParameterSource()
                    .addValue("id",         e.getId().toString())
                    .addValue("name",        e.getName())
                    .addValue("email",       e.getEmail())
                    .addValue("isActive",    e.getActiveFlag())
                    .addValue("createdBy",   e.getCreatedBy())
                    .addValue("createdAt",   e.getCreatedTs())
                    .addValue("updatedAt",   e.getUpdatedTs())
                    .addValue("updatedBy",   e.getUpdatedBy());
        }

        @Override
        protected MapSqlParameterSource getUpdateParams(UserEntity e, UUID id) {
            return new MapSqlParameterSource()
                    .addValue("id",    id.toString())
                    .addValue("name",  e.getName())
                    .addValue("email", e.getEmail());
        }

        @Override
        protected MapSqlParameterSource getIdParam(UUID id) {
            return new MapSqlParameterSource("id", id.toString());
        }
    }

    // --- Spring test configuration ---

    @Configuration
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .addScript("classpath:test-schema.sql")
                    .build();
        }

        @Bean
        public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource dataSource) {
            return new NamedParameterJdbcTemplate(dataSource);
        }

        @Bean
        public UserRepository userRepository(NamedParameterJdbcTemplate template) {
            return new UserRepository(template);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Autowired
    private UserRepository userRepository;

    // --- Helpers ---

    private UserEntity buildUser(String name, String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail(email);
        user.setActiveFlag(true);
        user.setCreatedTs(Instant.now());
        user.setCreatedBy("test");
        user.setUpdatedTs(Instant.now().plusSeconds(30L));
        user.setUpdatedBy("test");
        return user;
    }

    // --- findById ---

    @Test
    void shouldFindEntityByIdAfterInsert() {
        UserEntity user = buildUser("Alice", "alice@example.com");
        userRepository.insert(user);

        Optional<UserEntity> found = userRepository.findById(user.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Alice");
        assertThat(found.get().getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void shouldReturnEmptyOptionalForUnknownId() {
        assertThat(userRepository.findById(UUID.randomUUID())).isEmpty();
    }

    // --- findAll ---

    @Test
    void shouldReturnAllInsertedEntities() {
        userRepository.insert(buildUser("Alice", "alice@example.com"));
        userRepository.insert(buildUser("Bob", "bob@example.com"));

        List<UserEntity> all = userRepository.findAll();

        assertThat(all).hasSize(2);
        assertThat(all).extracting(UserEntity::getName)
                .containsExactly("Alice", "Bob"); // sorted by name
    }

    @Test
    void shouldReturnEmptyListWhenNoEntitiesExist() {
        assertThat(userRepository.findAll()).isEmpty();
    }

    // --- count ---

    @Test
    void shouldReturnCorrectCount() {
        assertThat(userRepository.count()).isEqualTo(0);

        userRepository.insert(buildUser("Alice", "a@example.com"));
        assertThat(userRepository.count()).isEqualTo(1);

        userRepository.insert(buildUser("Bob", "b@example.com"));
        assertThat(userRepository.count()).isEqualTo(2);
    }

    // --- insert ---

    @Test
    void insertShouldReturnOne() {
        assertThat(userRepository.insert(buildUser("Alice", "a@example.com"))).isEqualTo(1);
    }

    @Test
    void insertShouldThrowCoreJdbcExceptionOnDuplicatePrimaryKey() {
        UserEntity user = buildUser("Alice", "a@example.com");
        userRepository.insert(user);

        assertThatThrownBy(() -> userRepository.insert(user))
                .isInstanceOf(CoreJdbcException.class);
    }

    // --- update ---

    @Test
    void shouldUpdateEntityFields() {
        UserEntity user = buildUser("Alice", "alice@example.com");
        userRepository.insert(user);

        user.setName("Alice Updated");
        user.setEmail("updated@example.com");
        userRepository.update(user, user.getId());

        Optional<UserEntity> updated = userRepository.findById(user.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Alice Updated");
        assertThat(updated.get().getEmail()).isEqualTo("updated@example.com");
    }

    @Test
    void updateShouldThrowCoreJdbcExceptionWhenEntityDoesNotExist() {
        UserEntity ghost = buildUser("Ghost", "ghost@example.com");
        assertThatThrownBy(() -> userRepository.update(ghost, ghost.getId()))
                .isInstanceOf(CoreJdbcException.class)
                .hasMessageContaining("Update operation affected 0 rows");
    }

    // --- deleteById ---

    @Test
    void shouldDeleteEntityById() {
        UserEntity user = buildUser("Alice", "alice@example.com");
        userRepository.insert(user);
        assertThat(userRepository.count()).isEqualTo(1);

        userRepository.deleteById(user.getId());

        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    void deleteByIdShouldReturnOneWhenEntityWasDeleted() {
        UserEntity user = buildUser("Alice", "alice@example.com");
        userRepository.insert(user);

        assertThat(userRepository.deleteById(user.getId())).isEqualTo(1);
    }

    @Test
    void deleteByIdShouldReturnZeroForUnknownId() {
        assertThat(userRepository.deleteById(UUID.randomUUID())).isEqualTo(0);
    }

    // --- deleteAll ---

    @Test
    void shouldDeleteAllEntities() {
        userRepository.insert(buildUser("Alice", "a@example.com"));
        userRepository.insert(buildUser("Bob", "b@example.com"));
        assertThat(userRepository.count()).isEqualTo(2);

        userRepository.deleteAll();

        assertThat(userRepository.count()).isEqualTo(0);
    }

    @Test
    void deleteAllShouldNotThrowWhenTableIsAlreadyEmpty() {
        assertThatNoException().isThrownBy(() -> userRepository.deleteAll());
    }
}
