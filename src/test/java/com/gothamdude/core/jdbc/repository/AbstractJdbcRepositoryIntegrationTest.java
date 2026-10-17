package com.gothamdude.core.jdbc.repository;

import com.gothamdude.core.jdbc.exception.CoreJdbcException;
import com.gothamdude.core.jdbc.model.AuditedEntity;
import com.gothamdude.core.jdbc.model.AuditorProvider;
import com.gothamdude.core.jdbc.model.DomainEntity;
import com.gothamdude.core.jdbc.model.Versionable;
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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

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
                e.setActive(rs.getBoolean("is_active"));
                e.setCreatedBy(rs.getString("created_by"));
                Timestamp createdAt = rs.getTimestamp("created_at");
                e.setCreatedTs(createdAt == null ? null : createdAt.toInstant());
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                e.setUpdatedTs(updatedAt == null ? null : updatedAt.toInstant());
                e.setUpdatedBy(rs.getString("updated_by"));
                return e;
            };
        }

        @Override
        protected MapSqlParameterSource getInsertParams(UserEntity e) {
            return new MapSqlParameterSource()
                    .addValue("id",         e.getId().toString())
                    .addValue("name",        e.getName())
                    .addValue("email",       e.getEmail())
                    .addValue("isActive",    e.isActive())
                    .addValue("createdBy",   e.getCreatedBy())
                    .addValue("createdAt",   e.getCreatedTs())
                    .addValue("updatedAt",   e.getUpdatedTs())
                    .addValue("updatedBy",   e.getUpdatedBy());
        }

        @Override
        protected MapSqlParameterSource getUpdateParams(UserEntity e) {
            return new MapSqlParameterSource()
                    .addValue("id",    e.getId().toString())
                    .addValue("name",  e.getName())
                    .addValue("email", e.getEmail());
        }

        @Override
        protected MapSqlParameterSource getIdParam(UUID id) {
            return new MapSqlParameterSource("id", id.toString());
        }
    }

    // --- Test entity with a DB-generated key and the full capability set ---

    static class ProductEntity extends DomainEntity<Long> implements Versionable {
        private Long id;
        private String name;
        private long version;

        public Long getId()                  { return id; }
        public void setId(Long id)           { this.id = id; }
        public String getName()              { return name; }
        public void setName(String name)     { this.name = name; }
        @Override public long getVersion()           { return version; }
        @Override public void setVersion(long version) { this.version = version; }
    }

    // Repository that lets the base class contribute every audit / soft-delete /
    // version parameter via the reserved names — the subclass names only :name and :id.
    static class ProductRepository extends AbstractJdbcRepository<ProductEntity, Long> {

        ProductRepository(NamedParameterJdbcTemplate template, Clock clock, AuditorProvider auditorProvider) {
            super(template, clock, auditorProvider);
        }

        @Override protected String getTableName()  { return "products"; }
        @Override protected boolean isSoftDelete()  { return true; }

        @Override protected String getInsertSql() {
            return "INSERT INTO products (name, created_ts, created_by, updated_ts, updated_by, active, version) " +
                   "VALUES (:name, :createdTs, :createdBy, :updatedTs, :updatedBy, :active, :version)";
        }
        @Override protected String getUpdateSql() {
            return "UPDATE products SET name=:name, updated_ts=:updatedTs, updated_by=:updatedBy, version=version+1 " +
                   "WHERE id=:id AND version=:version";
        }
        @Override protected String getFindByIdSql() { return "SELECT * FROM products WHERE id=:id"; }
        @Override protected String getFindAllSql()  { return "SELECT * FROM products ORDER BY id"; }
        @Override protected String getCountSql()    { return "SELECT COUNT(*) FROM products"; }

        @Override
        protected RowMapper<ProductEntity> getRowMapper() {
            return (rs, rowNum) -> {
                ProductEntity p = new ProductEntity();
                p.setId(rs.getLong("id"));
                p.setName(rs.getString("name"));
                Timestamp createdTs = rs.getTimestamp("created_ts");
                p.setCreatedTs(createdTs == null ? null : createdTs.toInstant());
                p.setCreatedBy(rs.getString("created_by"));
                Timestamp updatedTs = rs.getTimestamp("updated_ts");
                p.setUpdatedTs(updatedTs == null ? null : updatedTs.toInstant());
                p.setUpdatedBy(rs.getString("updated_by"));
                p.setActive(rs.getBoolean("active"));
                p.setVersion(rs.getLong("version"));
                return p;
            };
        }

        @Override
        protected MapSqlParameterSource getInsertParams(ProductEntity e) {
            return new MapSqlParameterSource("name", e.getName());
        }

        @Override
        protected MapSqlParameterSource getUpdateParams(ProductEntity e) {
            return new MapSqlParameterSource()
                    .addValue("name", e.getName())
                    .addValue("id", e.getId());
        }

        @Override
        protected MapSqlParameterSource getIdParam(Long id) {
            return new MapSqlParameterSource("id", id);
        }

        // --- custom finders built on the protected query helpers ---

        List<ProductEntity> findByNameContains(String fragment) {
            return queryList("SELECT * FROM products WHERE name LIKE :frag ORDER BY id",
                    params("frag", "%" + fragment + "%"));
        }

        List<ProductEntity> findAllActive() {
            return queryList("SELECT * FROM products WHERE 1=1" + activePredicate() + " ORDER BY id", params());
        }

        List<ProductEntity> findFromMissingTable() {
            return queryList("SELECT * FROM does_not_exist", params());
        }

        // Uses the raw JdbcTemplate (Option B) with positional '?' params —
        // the kind of thing NamedParameterJdbcTemplate is not the tool for.
        long countByNamePrefixViaJdbcTemplate(String prefix) {
            Long n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM products WHERE name LIKE ?", Long.class, prefix + "%");
            return n == null ? 0L : n;
        }

        // Custom insert that reuses the protected insertParams(...) wrapper, so the
        // audit / version params are contributed without naming them here.
        int insertViaReusableParams(ProductEntity e) {
            beforeInsert(e);
            return execUpdate(getInsertSql(), insertParams(e));
        }
    }

    // --- AuditedEntity fixture: audited, no soft delete, no version ---

    static class NoteEntity extends AuditedEntity<Long> {
        private Long id;
        private String body;

        public Long getId()              { return id; }
        public void setId(Long id)       { this.id = id; }
        public String getBody()          { return body; }
        public void setBody(String body) { this.body = body; }
    }

    static class NoteRepository extends AbstractJdbcRepository<NoteEntity, Long> {

        NoteRepository(NamedParameterJdbcTemplate template, Clock clock, AuditorProvider auditorProvider) {
            super(template, clock, auditorProvider);
        }

        @Override protected String getTableName() { return "notes"; }

        @Override protected String getInsertSql() {
            return "INSERT INTO notes (body, created_ts, created_by, updated_ts, updated_by) " +
                   "VALUES (:body, :createdTs, :createdBy, :updatedTs, :updatedBy)";
        }
        @Override protected String getUpdateSql() {
            return "UPDATE notes SET body=:body, updated_ts=:updatedTs, updated_by=:updatedBy WHERE id=:id";
        }
        @Override protected String getFindByIdSql() { return "SELECT * FROM notes WHERE id=:id"; }

        @Override protected MapSqlParameterSource getInsertParams(NoteEntity e) {
            return new MapSqlParameterSource("body", e.getBody());
        }
        @Override protected MapSqlParameterSource getUpdateParams(NoteEntity e) {
            return new MapSqlParameterSource().addValue("body", e.getBody()).addValue("id", e.getId());
        }
        @Override protected MapSqlParameterSource getIdParam(Long id) {
            return new MapSqlParameterSource("id", id);
        }

        @Override protected RowMapper<NoteEntity> getRowMapper() {
            return (rs, rowNum) -> {
                NoteEntity n = new NoteEntity();
                n.setId(rs.getLong("id"));
                n.setBody(rs.getString("body"));
                java.sql.Timestamp createdTs = rs.getTimestamp("created_ts");
                n.setCreatedTs(createdTs == null ? null : createdTs.toInstant());
                n.setCreatedBy(rs.getString("created_by"));
                java.sql.Timestamp updatedTs = rs.getTimestamp("updated_ts");
                n.setUpdatedTs(updatedTs == null ? null : updatedTs.toInstant());
                n.setUpdatedBy(rs.getString("updated_by"));
                return n;
            };
        }
    }

    // --- Spring test configuration ---

    @Configuration
    static class TestConfig {

        // A fixed clock + known auditor so audit-stamping assertions are deterministic.
        static final Instant FIXED_INSTANT = Instant.parse("2026-01-02T03:04:05Z");

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
        public ProductRepository productRepository(NamedParameterJdbcTemplate template) {
            return new ProductRepository(template, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), () -> "tester");
        }

        @Bean
        public NoteRepository noteRepository(NamedParameterJdbcTemplate template) {
            return new NoteRepository(template, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC), () -> "tester");
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private NoteRepository noteRepository;

    // --- Helpers ---

    private UserEntity buildUser(String name, String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setEmail(email);
        user.setActive(true);
        user.setCreatedTs(Instant.now());
        user.setCreatedBy("test");
        user.setUpdatedTs(Instant.now().plusSeconds(30L));
        user.setUpdatedBy("test");
        return user;
    }

    // Audit / active / version are intentionally left unset so the library stamps them.
    private ProductEntity buildProduct(String name) {
        ProductEntity product = new ProductEntity();
        product.setName(name);
        return product;
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
                .isInstanceOfSatisfying(CoreJdbcException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CoreJdbcException.ErrorCode.DUPLICATE_KEY));
    }

    @Test
    void insertShouldStampAuditFieldsAndRoundTrip() {
        UserEntity user = buildUser("Audie", "audie@example.com");
        userRepository.insert(user);

        UserEntity loaded = userRepository.findById(user.getId()).orElseThrow();
        // createdBy is preserved from the caller; updatedBy is stamped by beforeInsert
        // from the (default "system") AuditorProvider. Both survive the round trip now
        // that the mapper reads every audit column.
        assertThat(loaded.getCreatedBy()).isEqualTo("test");
        assertThat(loaded.getUpdatedBy()).isEqualTo("system");
        assertThat(loaded.getCreatedTs()).isNotNull();
        assertThat(loaded.getUpdatedTs()).isNotNull();
    }

    // --- update ---

    @Test
    void shouldUpdateEntityFields() {
        UserEntity user = buildUser("Alice", "alice@example.com");
        userRepository.insert(user);

        user.setName("Alice Updated");
        user.setEmail("updated@example.com");
        userRepository.update(user);

        Optional<UserEntity> updated = userRepository.findById(user.getId());
        assertThat(updated).isPresent();
        assertThat(updated.get().getName()).isEqualTo("Alice Updated");
        assertThat(updated.get().getEmail()).isEqualTo("updated@example.com");
    }

    @Test
    void updateShouldThrowCoreJdbcExceptionWhenEntityDoesNotExist() {
        UserEntity ghost = buildUser("Ghost", "ghost@example.com");
        assertThatThrownBy(() -> userRepository.update(ghost))
                .isInstanceOfSatisfying(CoreJdbcException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CoreJdbcException.ErrorCode.NOT_FOUND))
                .hasMessageContaining("No row with id");
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

    // ================================================================
    // Capability tests on ProductRepository (DB-generated key + mixins)
    // ================================================================

    // --- Phase 05: generated keys ---

    @Test
    void insertAndReturnKeyShouldReturnGeneratedKeyAndSetItOnEntity() {
        ProductEntity product = buildProduct("Widget");

        Long id = productRepository.insertAndReturnKey(product);

        assertThat(id).isNotNull();
        assertThat(product.getId()).isEqualTo(id);
        assertThat(productRepository.findById(id)).isPresent();
    }

    // --- Phase 04: audit stamping (deterministic via fixed clock) ---

    @Test
    void insertShouldStampCreateAndUpdateAuditFromClockAndAuditor() {
        ProductEntity product = buildProduct("Gadget");

        Long id = productRepository.insertAndReturnKey(product);

        ProductEntity loaded = productRepository.findById(id).orElseThrow();
        assertThat(loaded.getCreatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
        assertThat(loaded.getCreatedBy()).isEqualTo("tester");
        assertThat(loaded.getUpdatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
        assertThat(loaded.getUpdatedBy()).isEqualTo("tester");
    }

    // --- Phase 06: batch writes ---

    @Test
    void insertBatchShouldInsertEveryRowWithAuditStamped() {
        List<ProductEntity> batch = IntStream.rangeClosed(1, 1000)
                .mapToObj(i -> buildProduct("P" + i))
                .toList();

        int[] counts = productRepository.insertBatch(batch);

        assertThat(counts).hasSize(1000);
        assertThat(productRepository.count()).isEqualTo(1000L);
        assertThat(productRepository.findAll()).allSatisfy(p -> {
            assertThat(p.getCreatedBy()).isEqualTo("tester");
            assertThat(p.getCreatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
        });
    }

    @Test
    void insertBatchShouldReturnEmptyArrayForEmptyList() {
        assertThat(productRepository.insertBatch(List.of())).isEmpty();
    }

    // --- Phase 07: paging ---

    @Test
    void findAllWithPagingShouldLimitContentAndReportTotal() {
        for (int i = 1; i <= 5; i++) {
            productRepository.insert(buildProduct("Item" + i));
        }

        PageResult<ProductEntity> page = productRepository.findAll(new PageRequest(2, 0));

        assertThat(page.content()).hasSize(2);
        assertThat(page.limit()).isEqualTo(2);
        assertThat(page.offset()).isEqualTo(0);
        assertThat(page.total()).isEqualTo(5L);
    }

    @Test
    void pageRequestShouldRejectInvalidLimit() {
        assertThatThrownBy(() -> new PageRequest(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageRequest(1, -1)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- Phase 07: soft delete ---

    @Test
    void deleteByIdShouldSoftDeleteAndBumpAuditWhenSoftDeleteEnabled() {
        ProductEntity product = buildProduct("ToRetire");
        Long id = productRepository.insertAndReturnKey(product);

        int affected = productRepository.deleteById(id);

        assertThat(affected).isEqualTo(1);
        ProductEntity after = productRepository.findById(id).orElseThrow();
        assertThat(after.isActive()).isFalse();
        assertThat(after.getUpdatedBy()).isEqualTo("tester");
        assertThat(after.getUpdatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
    }

    @Test
    void activePredicateFinderShouldExcludeSoftDeletedRows() {
        Long idA = productRepository.insertAndReturnKey(buildProduct("Active1"));
        productRepository.insertAndReturnKey(buildProduct("Active2"));
        productRepository.deleteById(idA);

        assertThat(productRepository.findAllActive())
                .extracting(ProductEntity::getName)
                .containsExactly("Active2");
    }

    // --- Phase 08: optimistic locking ---

    @Test
    void updateShouldBumpVersionInDbAndInMemory() {
        ProductEntity product = buildProduct("Bumpy");
        Long id = productRepository.insertAndReturnKey(product);

        ProductEntity loaded = productRepository.findById(id).orElseThrow();
        assertThat(loaded.getVersion()).isEqualTo(0L);

        loaded.setName("Bumpy v2");
        productRepository.update(loaded);

        assertThat(loaded.getVersion()).isEqualTo(1L); // in-memory bump
        ProductEntity reloaded = productRepository.findById(id).orElseThrow();
        assertThat(reloaded.getVersion()).isEqualTo(1L);
        assertThat(reloaded.getName()).isEqualTo("Bumpy v2");
    }

    @Test
    void staleUpdateShouldThrowOptimisticLock() {
        Long id = productRepository.insertAndReturnKey(buildProduct("Versioned"));

        ProductEntity stale = productRepository.findById(id).orElseThrow(); // version 0
        ProductEntity fresh = productRepository.findById(id).orElseThrow(); // version 0

        fresh.setName("winner");
        productRepository.update(fresh); // DB version 0 -> 1

        stale.setName("loser");
        assertThatThrownBy(() -> productRepository.update(stale))
                .isInstanceOfSatisfying(CoreJdbcException.class, ex ->
                        assertThat(ex.getErrorCode()).isEqualTo(CoreJdbcException.ErrorCode.OPTIMISTIC_LOCK));
    }

    // --- Phase 02: custom finders through the query helpers ---

    @Test
    void customFinderShouldReturnMatchingRows() {
        productRepository.insert(buildProduct("Alpha"));
        productRepository.insert(buildProduct("Alphabet"));
        productRepository.insert(buildProduct("Beta"));

        assertThat(productRepository.findByNameContains("Alpha"))
                .extracting(ProductEntity::getName)
                .containsExactly("Alpha", "Alphabet");
    }

    @Test
    void customFinderShouldWrapDataAccessErrorsInCoreJdbcException() {
        assertThatThrownBy(() -> productRepository.findFromMissingTable())
                .isInstanceOf(CoreJdbcException.class);
    }

    // --- Option B: the exposed JdbcTemplate works end-to-end with positional params ---

    @Test
    void repositoryCanUseExposedJdbcTemplateWithPositionalParams() {
        productRepository.insert(buildProduct("Zeta-1"));
        productRepository.insert(buildProduct("Zeta-2"));
        productRepository.insert(buildProduct("Other"));

        assertThat(productRepository.countByNamePrefixViaJdbcTemplate("Zeta")).isEqualTo(2L);
    }

    // --- custom SQL can reuse the protected insertParams(...) audit contribution ---

    @Test
    void subclassCanReuseInsertParamsForCustomInsertWithAuditStamped() {
        int rows = productRepository.insertViaReusableParams(buildProduct("Reused"));

        assertThat(rows).isEqualTo(1);
        ProductEntity loaded = productRepository.findByNameContains("Reused").get(0);
        assertThat(loaded.getCreatedBy()).isEqualTo("tester");
        assertThat(loaded.getCreatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
        assertThat(loaded.getUpdatedBy()).isEqualTo("tester");
    }

    // --- AuditedEntity: audited record, no soft delete / version ---

    @Test
    void auditedEntityInsertStampsCreateAndUpdateAudit() {
        NoteEntity note = new NoteEntity();
        note.setBody("first");

        Long id = noteRepository.insertAndReturnKey(note);

        NoteEntity loaded = noteRepository.findById(id).orElseThrow();
        assertThat(loaded.getBody()).isEqualTo("first");
        assertThat(loaded.getCreatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
        assertThat(loaded.getCreatedBy()).isEqualTo("tester");
        assertThat(loaded.getUpdatedTs()).isEqualTo(TestConfig.FIXED_INSTANT);
        assertThat(loaded.getUpdatedBy()).isEqualTo("tester");
    }

    @Test
    void auditedEntityUpdateBumpsUpdateAuditAndKeepsCreateAudit() {
        NoteEntity note = new NoteEntity();
        note.setBody("draft");
        Long id = noteRepository.insertAndReturnKey(note);

        note.setBody("final");
        noteRepository.update(note);

        NoteEntity loaded = noteRepository.findById(id).orElseThrow();
        assertThat(loaded.getBody()).isEqualTo("final");
        assertThat(loaded.getCreatedBy()).isEqualTo("tester");   // create audit preserved
        assertThat(loaded.getUpdatedBy()).isEqualTo("tester");   // update audit stamped
    }
}
