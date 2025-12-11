package com.permacore.codegen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;

/**
 * Code generator entry point in the codegen module.
 */
public class CodeGeneratorMain {

    private static String DB_URL = "jdbc:mysql://localhost:3306/permacore_iam?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8";
    private static String DB_USERNAME = "root";
    private static String DB_PASSWORD = "123456";

    private static final String PROJECT_PATH = System.getProperty("user.dir");
    private static final String JAVA_PATH = "/src/main/java";
    private static final String RESOURCES_PATH = "/src/main/resources";

    public static void main(String[] args) {
        System.out.println("[CodeGeneratorMain] generator module running, project path=" + PROJECT_PATH);
        boolean useH2 = Boolean.parseBoolean(System.getProperty("useH2", "false"));
        if (useH2) {
            try {
                Class.forName("org.h2.Driver");
            } catch (Exception e) {
                System.err.println("H2 driver load failed: " + e.getMessage());
            }
            DB_URL = "jdbc:h2:mem:permacore_iam;DB_CLOSE_DELAY=-1;MODE=MySQL";
            DB_USERNAME = "sa";
            DB_PASSWORD = "";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USERNAME, DB_PASSWORD);
                 Statement st = conn.createStatement()) {
                String[] tables = new String[]{"sys_user","sys_role","sys_dept","sys_permission","sys_role_permission","sys_user_role","sys_role_inheritance","sys_sod_constraint","sys_oper_log","sys_login_log","sys_jwt_version"};
                String baseColumns = "id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(200), del_flag INT DEFAULT 0, create_time TIMESTAMP, update_time TIMESTAMP";
                for (String t : tables) {
                    String sql = String.format("CREATE TABLE IF NOT EXISTS %s (%s)", t, baseColumns);
                    st.execute(sql);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        FastAutoGenerator.create(DB_URL, DB_USERNAME, DB_PASSWORD)
                .globalConfig(builder -> builder.author("PermaCore团队").outputDir(PROJECT_PATH + JAVA_PATH))
                .packageConfig(builder -> builder.parent("com.permacore.iam").entity("domain.entity").pathInfo(Collections.singletonMap(OutputFile.xml, PROJECT_PATH + RESOURCES_PATH + "/mapper")))
                .strategyConfig(builder -> builder.addInclude("sys_user","sys_role","sys_dept","sys_permission","sys_role_permission","sys_user_role","sys_role_inheritance","sys_sod_constraint","sys_oper_log","sys_login_log","sys_jwt_version").entityBuilder().enableLombok().formatFileName("%sEntity").mapperBuilder().formatMapperFileName("%sMapper").serviceBuilder().formatServiceFileName("%sService").formatServiceImplFileName("%sServiceImpl").controllerBuilder().formatFileName("%sController"))
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

        System.out.println("generator finished");
    }
}

