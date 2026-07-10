package com.permacore.iam.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SysUserMapperLockSqlContractTest {

    @Test
    void activeUserMutationLockIsAByIdForUpdateQuery() throws IOException {
        String mapperXml = Files.readString(
                Path.of("src/main/resources/mapper/SysUserMapper.xml"),
                StandardCharsets.UTF_8
        );

        assertThat(mapperXml)
                .contains("<select id=\"selectByIdForUpdate\"")
                .contains("WHERE id = #{userId} AND del_flag = 0")
                .contains("FOR UPDATE");
    }
}
