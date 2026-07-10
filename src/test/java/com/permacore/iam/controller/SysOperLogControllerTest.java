package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.SysOperLogEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysOperLogService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.lang.reflect.Method;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysOperLogControllerTest {

    @Mock
    private SysOperLogService operLogService;

    private SysOperLogController controller;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                SysOperLogEntity.class
        );
        controller = new SysOperLogController(operLogService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageOmitsLargeTextColumnsWhileKeepingPaginationContract() {
        Page<SysOperLogEntity> databasePage = new Page<>(1, 100);
        databasePage.setTotal(456);
        when(operLogService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(databasePage);

        Result<PageVO<SysOperLogEntity>> result = controller.page(1, 100, null, null, null, null, null);

        ArgumentCaptor<Page<SysOperLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<SysOperLogEntity>> wrapperCaptor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(operLogService).page(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
        assertThat(result.getData().getTotal()).isEqualTo(456);
        assertThat(wrapperCaptor.getValue().getSqlSelect())
                .contains("id", "title", "oper_time")
                .doesNotContain("oper_param", "json_result", "error_msg");
        assertThat(compactSql(wrapperCaptor.getValue().getCustomSqlSegment()))
                .contains("orderbyoper_timedesc,iddesc");
    }

    @Test
    void detailStillReturnsTheCompleteEntity() {
        SysOperLogEntity fullLog = new SysOperLogEntity();
        fullLog.setId(9L);
        fullLog.setOperParam("{\"request\":true}");
        fullLog.setJsonResult("{\"success\":true}");
        fullLog.setErrorMsg("complete error details");
        when(operLogService.getById(9L)).thenReturn(fullLog);

        Result<SysOperLogEntity> result = controller.getById(9L);

        assertThat(result.getData()).isSameAs(fullLog);
        assertThat(result.getData().getOperParam()).isEqualTo("{\"request\":true}");
        assertThat(result.getData().getJsonResult()).isEqualTo("{\"success\":true}");
        assertThat(result.getData().getErrorMsg()).isEqualTo("complete error details");
    }

    @Test
    void pageSizeAboveOneHundredIsRejectedByMethodValidation() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Method pageMethod = SysOperLogController.class.getMethod(
                "page", Integer.class, Integer.class, String.class, String.class, Integer.class, String.class, String.class);

        Set<ConstraintViolation<SysOperLogController>> violations = validator.forExecutables().validateParameters(
                controller, pageMethod, new Object[]{1, 101, null, null, null, null, null});

        assertThat(violations).extracting(ConstraintViolation::getMessage).contains("pageSize不能超过100");
    }

    private String compactSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", "").toLowerCase();
    }
}
