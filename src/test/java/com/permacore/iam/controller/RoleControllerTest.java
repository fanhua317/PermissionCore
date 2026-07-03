package com.permacore.iam.controller;

import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.service.SysRoleInheritanceService;
import com.permacore.iam.service.SysRoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class RoleControllerTest {

    @Mock
    private SysRoleService roleService;
    @Mock
    private SysRoleInheritanceService roleInheritanceService;

    private RoleController controller;

    @BeforeEach
    void setUp() {
        controller = new RoleController(roleService, roleInheritanceService);
    }

    @Test
    void legacyInheritanceEndpointDelegatesToValidatedInheritanceService() {
        Result<Void> result = controller.setInheritance(1L, 2L);

        assertThat(result.getCode()).isEqualTo(200);
        verify(roleInheritanceService).addInheritance(1L, 2L);
        verifyNoInteractions(roleService);
    }
}
