package com.permacore.iam.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserMapperSqlContractTest {

    @Test
    void listQueryFetchesIdsBeforeRowsAndOmitsSensitiveColumns() throws IOException {
        String mapperXml = readResource("mapper/SysUserMapper.xml");
        String select = element(mapperXml, "<select id=\"selectUserPage\"", "</select>");

        assertThat(select)
                .contains("SELECT u.id")
                .contains("ORDER BY u.create_time DESC, u.id DESC")
                .contains("LIMIT #{offset}, #{pageSize}")
                .contains("INNER JOIN sys_user user_row ON user_row.id = page_ids.id")
                .doesNotContain("password", "auth_version", "SELECT *");
    }

    @Test
    void mapperDocumentsLeadingWildcardContractAndStableTieBreaker() throws IOException {
        String mapperXml = readResource("mapper/SysUserMapper.xml");

        assertThat(mapperXml)
                .contains("Leading-wildcard LIKE cannot use a normal B-tree index")
                .contains("u.username LIKE CONCAT('%', #{username}, '%')")
                .contains("u.nickname LIKE CONCAT('%', #{nickname}, '%')")
                .contains("ORDER BY user_row.create_time DESC, user_row.id DESC");
    }

    @Test
    void freshSchemaAndMigrationDeclareOnlyRequiredCoveringIndexes() throws IOException {
        String schema = readResource("db/schema.sql");
        String migration = readResource("db/migrations/20260710_optimize_user_queries.sql");

        assertThat(schema)
                .contains("idx_user_active_created (del_flag, create_time DESC, id DESC)")
                .contains("idx_user_role_role_user (role_id, user_id)")
                .contains("idx_role_inheritance_desc_anc (descendant_id, ancestor_id)")
                .contains("idx_login_time_id (login_time DESC, id DESC)")
                .contains("idx_oper_time_id (oper_time DESC, id DESC)")
                .doesNotContain("idx_role_permission_permission_role");
        assertThat(migration)
                .contains("information_schema.STATISTICS")
                .contains("indexed_columns = 'del_flag,create_time,id'")
                .contains("indexed_columns = 'role_id,user_id'")
                .contains("indexed_columns = 'descendant_id,ancestor_id'")
                .contains("indexed_columns = 'login_time,id'")
                .contains("indexed_columns = 'oper_time,id'")
                .contains("Its only")
                .contains("reverse operation is DELETE");
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
