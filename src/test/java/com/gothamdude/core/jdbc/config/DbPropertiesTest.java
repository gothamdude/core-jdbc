package com.gothamdude.core.jdbc.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class DbPropertiesTest {

//    Will not work for H2
//    @Test
//    void shouldHaveDefaultDriverClassName() {
//        assertThat(new DbProperties().getDriverClassName()).isEqualTo("org.postgresql.Driver");
//    }

    @Test
    void shouldHaveDefaultMaxPoolSize() {
        assertThat(new DbProperties().getMaxPoolSize()).isEqualTo(10);
    }

    @Test
    void shouldHaveDefaultMinPoolSize() {
        assertThat(new DbProperties().getMinPoolSize()).isEqualTo(2);
    }

    @Test
    void shouldHaveDefaultQueryTimeout() {
        assertThat(new DbProperties().getQueryTimeout()).isEqualTo(30);
    }

    @Test
    void shouldHaveDefaultFetchSize() {
        assertThat(new DbProperties().getFetchSize()).isEqualTo(1000);
    }

    @Test
    void shouldSetAndGetUrl() {
        DbProperties props = new DbProperties();
        props.setUrl("jdbc:postgresql://host:5432/mydb");
        assertThat(props.getUrl()).isEqualTo("jdbc:postgresql://host:5432/mydb");
    }

    @Test
    void shouldSetAndGetUsername() {
        DbProperties props = new DbProperties();
        props.setUsername("appuser");
        assertThat(props.getUsername()).isEqualTo("appuser");
    }

    @Test
    void shouldSetAndGetPassword() {
        DbProperties props = new DbProperties();
        props.setPassword("s3cret");
        assertThat(props.getPassword()).isEqualTo("s3cret");
    }

    @Test
    void shouldSetAndGetMaxPoolSize() {
        DbProperties props = new DbProperties();
        props.setMaxPoolSize(20);
        assertThat(props.getMaxPoolSize()).isEqualTo(20);
    }

    @Test
    void shouldSetAndGetMinPoolSize() {
        DbProperties props = new DbProperties();
        props.setMinPoolSize(5);
        assertThat(props.getMinPoolSize()).isEqualTo(5);
    }

    @Test
    void shouldSetAndGetQueryTimeout() {
        DbProperties props = new DbProperties();
        props.setQueryTimeout(60);
        assertThat(props.getQueryTimeout()).isEqualTo(60);
    }

    @Test
    void shouldSetAndGetFetchSize() {
        DbProperties props = new DbProperties();
        props.setFetchSize(500);
        assertThat(props.getFetchSize()).isEqualTo(500);
    }

    @Test
    void shouldSetAndGetDriverClassName() {
        DbProperties props = new DbProperties();
        props.setDriverClassName("org.h2.Driver");
        assertThat(props.getDriverClassName()).isEqualTo("org.h2.Driver");
    }

    @Test
    void equalsShouldBeTrueForSameValues() {
        DbProperties a = new DbProperties();
        a.setUrl("jdbc:postgresql://localhost/db");
        a.setUsername("user");
        a.setPassword("pass");

        DbProperties b = new DbProperties();
        b.setUrl("jdbc:postgresql://localhost/db");
        b.setUsername("user");
        b.setPassword("pass");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void equalsShouldBeFalseForDifferentUrl() {
        DbProperties a = new DbProperties();
        a.setUrl("jdbc:postgresql://host-a/db");

        DbProperties b = new DbProperties();
        b.setUrl("jdbc:postgresql://host-b/db");

        assertThat(a).isNotEqualTo(b);
    }
}