package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.permacore.iam.domain.entity.SysLoginLogEntity;
import com.permacore.iam.domain.vo.PageVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysLoginLogService;
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
class SysLoginLogControllerTest {

    @Mock
    private SysLoginLogService loginLogService;

    private SysLoginLogController controller;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                SysLoginLogEntity.class
        );
        controller = new SysLoginLogController(loginLogService);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void pageKeepsTotalAndAcceptsTheMaximumPageSize() {
        Page<SysLoginLogEntity> databasePage = new Page<>(1, 100);
        databasePage.setTotal(321);
        when(loginLogService.page(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(databasePage);

        Result<PageVO<SysLoginLogEntity>> result = controller.page(1, 100, null, null, null, null);

        ArgumentCaptor<Page<SysLoginLogEntity>> pageCaptor = ArgumentCaptor.forClass(Page.class);
        ArgumentCaptor<LambdaQueryWrapper<SysLoginLogEntity>> wrapperCaptor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(loginLogService).page(pageCaptor.capture(), wrapperCaptor.capture());
        assertThat(pageCaptor.getValue().getSize()).isEqualTo(100);
        assertThat(result.getData().getTotal()).isEqualTo(321);
        assertThat(compactSql(wrapperCaptor.getValue().getCustomSqlSegment()))
                .contains("orderbylogin_timedesc,iddesc");
    }

    @Test
    void pageSizeAboveOneHundredIsRejectedByMethodValidation() throws Exception {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        Method pageMethod = SysLoginLogController.class.getMethod(
                "page", Integer.class, Integer.class, String.class, Integer.class, String.class, String.class);

        Set<ConstraintViolation<SysLoginLogController>> violations = validator.forExecutables().validateParameters(
                controller, pageMethod, new Object[]{1, 101, null, null, null, null});

        assertThat(violations).extracting(ConstraintViolation::getMessage).contains("pageSize不能超过100");
    }

    private String compactSql(String sql) {
        return sql == null ? "" : sql.replaceAll("\\s+", "").toLowerCase();
    }
}
