package com.permacore.iam.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationStateSqlContractTest {

    @Test
    void authorizationGateReadsPersonalAndGlobalVersionsInOneQuery() throws IOException {
        String mapperXml = readResource("mapper/SysUserMapper.xml");
        String select = element(mapperXml, "<select id=\"selectAuthorizationStateById\"", "</select>");

        assertThat(select)
                .contains("u.auth_version")
                .contains("state.global_auth_version")
                .contains("INNER JOIN sys_authorization_state state ON state.id = 1")
                .contains("WHERE u.id = #{userId}");
    }

    @Test
    void globalInvalidationUpdatesOnlyTheSingletonRow() throws IOException {
        String mapperXml = readResource("mapper/SysUserMapper.xml");
        String update = element(mapperXml, "<update id=\"incrementGlobalAuthVersion\"", "</update>");

        assertThat(update)
                .contains("UPDATE sys_authorization_state")
                .contains("global_auth_version = global_auth_version + 1")
                .contains("WHERE id = 1")
                .doesNotContain("sys_user");
        assertThat(mapperXml).doesNotContain("incrementAllActiveAuthVersions");
    }

    @Test
    void freshAndExistingDatabasesCreateTheSameSingletonAndInitUsesIt() throws IOException {
        String schema = readResource("db/schema.sql");
        String migration = readResource("db/migrations/20260710_add_auth_version.sql");
        String init = readResource("db/init-permissions.sql");

        assertThat(schema)
                .contains("CREATE TABLE IF NOT EXISTS sys_authorization_state")
                .contains("CONSTRAINT chk_authorization_state_singleton CHECK (id = 1)")
                .contains("VALUES (1, 0)");
        assertThat(migration)
                .contains("CREATE TABLE IF NOT EXISTS sys_authorization_state")
                .contains("VALUES (1, 0)")
                .contains("ON DUPLICATE KEY UPDATE");
        assertThat(init)
                .contains("UPDATE sys_authorization_state")
                .contains("global_auth_version = global_auth_version + 1")
                .doesNotContain("UPDATE sys_user SET auth_version = auth_version + 1");
    }

    private String readResource(String path) throws IOException {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("classpath resource %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String element(String xml, String startMarker, String endMarker) {
        int start = xml.indexOf(startMarker);
        assertThat(start).as("start marker %s", startMarker).isGreaterThanOrEqualTo(0);
        int end = xml.indexOf(endMarker, start);
        assertThat(end).as("end marker %s", endMarker).isGreaterThan(start);
        return xml.substring(start, end + endMarker.length());
    }
}
