import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

import java.util.Collections;

/**
 * MyBatis Plus 代码生成器
 * 运行方式：右键 -> Run
 */
public class CodeGenerator {

    // 数据库配置
    private static final String DB_URL = "jdbc:mysql://localhost:3306/permacore_iam?useUnicode=true&characterEncoding=utf8&serverTimezone=GMT%2B8";
    private static final String DB_USERNAME = "root";
    private static final String DB_PASSWORD = "123456"; // 请修改为您的密码

    // 项目路径
    private static final String PROJECT_PATH = System.getProperty("user.dir");
    private static final String JAVA_PATH = "/src/main/java";
    private static final String RESOURCES_PATH = "/src/main/resources";

    public static void main(String[] args) {
        FastAutoGenerator.create(DB_URL, DB_USERNAME, DB_PASSWORD)
                // 全局配置
                .globalConfig(builder -> {
                    builder.author("PermaCore团队")
                            .enableSwagger()
                            .outputDir(PROJECT_PATH + JAVA_PATH)
                            .commentDate("yyyy-MM-dd");
                })
                // 包配置
                .packageConfig(builder -> {
                    builder.parent("com.permacore.iam")
                            .entity("domain.entity")
                            .mapper("mapper")
                            .service("service")
                            .serviceImpl("service.impl")
                            .controller("controller")
                            .pathInfo(Collections.singletonMap(OutputFile.xml,
                                    PROJECT_PATH + RESOURCES_PATH + "/mapper"));
                })
                // 策略配置
                .strategyConfig(builder -> {
                    // 要生成的表名（按需修改）
                    builder.addInclude(
                                    "sys_user",
                                    "sys_role",
                                    "sys_dept",
                                    "sys_permission",
                                    "sys_role_permission",
                                    "sys_user_role",
                                    "sys_role_inheritance",
                                    "sys_sod_constraint",
                                    "sys_oper_log",
                                    "sys_login_log",
                                    "sys_jwt_version"
                            )
                            // Entity策略
                            .entityBuilder()
                            .enableLombok()
                            .enableTableFieldAnnotation()
                            .logicDeleteColumnName("del_flag")
                            .logicDeletePropertyName("delFlag")
                            // 加入 TableFill 需要确保 mybatis-plus-generator 的类在编译类路径，可根据需要恢复下面这两行
                            // .addTableFills(new com.baomidou.mybatisplus.generator.config.po.TableFill("create_time", com.baomidou.mybatisplus.generator.config.po.FieldFill.INSERT))
                            // .addTableFills(new com.baomidou.mybatisplus.generator.config.po.TableFill("update_time", com.baomidou.mybatisplus.generator.config.po.FieldFill.INSERT_UPDATE))
                            .formatFileName("%sEntity")
                            // Mapper策略
                            .mapperBuilder()
                            .enableMapperAnnotation()
                            .enableBaseResultMap()
                            .enableBaseColumnList()
                            .formatMapperFileName("%sMapper")
                            .formatXmlFileName("%sMapper")
                            // Service策略
                            .serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl")
                            // Controller策略
                            .controllerBuilder()
                            .enableRestStyle()
                            .formatFileName("%sController");
                })
                // 使用Freemarker引擎
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

        System.out.println("代码生成完成！请刷新项目查看。");
        // Create a marker file so external runners can detect completion
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get("target"));
            java.nio.file.Files.write(java.nio.file.Paths.get("target/codesgen.marker"), "done".getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}