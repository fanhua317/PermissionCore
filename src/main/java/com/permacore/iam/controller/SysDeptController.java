package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.permacore.iam.annotation.OperLog;
import com.permacore.iam.domain.entity.SysDeptEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.DeptTreeVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.domain.vo.DeptUpsertVO;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.mapper.SysDeptMapper;
import com.permacore.iam.service.SysDeptService;
import com.permacore.iam.security.handler.BusinessException;
import com.permacore.iam.domain.vo.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;

/**
 * 部门管理控制器
 */
@Tag(name = "部门管理", description = "组织架构管理")
@RestController
@RequestMapping("/api/dept")
@RequiredArgsConstructor
public class SysDeptController {

    private static final Logger log = LoggerFactory.getLogger(SysDeptController.class);

    private final SysDeptService deptService;
    private final SysUserMapper sysUserMapper;
    private final SysDeptMapper deptMapper;

    /**
     * 获取部门树形结构
     */
    @Operation(summary = "获取部门树", description = "获取部门树形结构及人数统计")
    @PreAuthorize("hasAuthority('system:dept:query')")
    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree() {
        List<SysDeptEntity> allDepts = deptService.list(
                new LambdaQueryWrapper<SysDeptEntity>()
                        .eq(SysDeptEntity::getDelFlag, 0)
                        .orderByAsc(SysDeptEntity::getSortOrder));

        Map<Long, Integer> directUserCountMap = buildDeptUserCountMap();
        List<DeptTreeVO> tree = buildDeptTree(allDepts, 0L, directUserCountMap, new HashSet<>());
        return Result.success(tree);
    }

    /**
     * 获取部门详情
     */
    @Operation(summary = "获取部门详情", description = "根据ID获取部门详情")
    @PreAuthorize("hasAuthority('system:dept:query')")
    @GetMapping("/{id}")
    public Result<SysDeptEntity> getById(@PathVariable Long id) {
        SysDeptEntity dept = deptService.getById(id);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        return Result.success(dept);
    }

    /**
     * 创建部门
     */
    @Operation(summary = "创建部门", description = "新增部门")
    @OperLog(title = "创建部门", businessType = 1)
    @PreAuthorize("hasAuthority('dept:add')")
    @PostMapping
    @Transactional
    public Result<Void> create(@Valid @RequestBody DeptUpsertVO vo) {
        deptMapper.lockAllDeptIds();
        validateDeptName(vo.getDeptName(), true);
        Long parentId = vo.getParentId() == null ? 0L : vo.getParentId();
        validateParentExists(parentId);
        byte targetStatus = vo.getStatus() == null ? (byte) 1 : vo.getStatus().byteValue();
        if (targetStatus == 1) {
            validateActiveParentChain(parentId);
        }
        SysDeptEntity dept = new SysDeptEntity();
        applyDeptFields(dept, vo);
        dept.setParentId(parentId);
        dept.setSortOrder(vo.getSortOrder() == null ? 0 : vo.getSortOrder());
        dept.setStatus(targetStatus);
        dept.setDelFlag((byte) 0);
        if (!deptService.save(dept)) {
            throw new BusinessException(ResultCode.ERROR, "创建部门失败");
        }
        log.info("创建部门: {}", dept.getDeptName());
        return Result.success();
    }

    /**
     * 更新部门
     */
    @Operation(summary = "更新部门", description = "更新部门信息")
    @OperLog(title = "更新部门", businessType = 2)
    @PreAuthorize("hasAuthority('dept:edit')")
    @PutMapping("/{id}")
    @Transactional
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DeptUpsertVO vo) {
        deptMapper.lockAllDeptIds();
        SysDeptEntity dept = deptService.getById(id);
        if (dept == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        validateDeptName(vo.getDeptName(), false);
        Long targetParentId = vo.getParentId() == null ? dept.getParentId() : vo.getParentId();
        byte targetStatus = vo.getStatus() == null
                ? (dept.getStatus() == null ? (byte) 1 : dept.getStatus())
                : vo.getStatus().byteValue();
        LambdaUpdateWrapper<SysDeptEntity> update = new LambdaUpdateWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getId, id)
                .eq(SysDeptEntity::getDelFlag, (byte) 0);
        boolean changed = false;
        if (vo.getParentId() != null) {
            Long parentId = vo.getParentId();
            validateParentExists(parentId);
            if (id.equals(parentId) || wouldCreateCycle(id, parentId)) {
                throw new BusinessException("上级部门不能是当前部门或其子部门");
            }
            update.set(SysDeptEntity::getParentId, parentId);
            changed = true;
        }
        if (vo.getDeptName() != null) {
            update.set(SysDeptEntity::getDeptName, vo.getDeptName().trim());
            changed = true;
        }
        if (vo.getLeaderId() != null) {
            update.set(SysDeptEntity::getLeaderId, vo.getLeaderId());
            changed = true;
        }
        if (vo.getPhone() != null) {
            update.set(SysDeptEntity::getPhone, vo.getPhone());
            changed = true;
        }
        if (vo.getEmail() != null) {
            update.set(SysDeptEntity::getEmail, vo.getEmail());
            changed = true;
        }
        if (vo.getSortOrder() != null) {
            update.set(SysDeptEntity::getSortOrder, vo.getSortOrder());
            changed = true;
        }
        if (vo.getStatus() != null) {
            if (targetStatus == 0 && Byte.valueOf((byte) 1).equals(dept.getStatus())) {
                validateCanDisable(id);
            }
            update.set(SysDeptEntity::getStatus, vo.getStatus().byteValue());
            changed = true;
        }
        if (targetStatus == 1) {
            validateActiveParentChain(targetParentId);
        }
        if (changed && !deptService.update(update)) {
            throw new BusinessException(ResultCode.ERROR, "更新部门失败");
        }
        log.info("更新部门: deptId={}", id);
        return Result.success();
    }

    /**
     * 删除部门
     */
    @Operation(summary = "删除部门", description = "删除部门（若有子部门则无法删除）")
    @OperLog(title = "删除部门", businessType = 3)
    @PreAuthorize("hasAuthority('dept:delete')")
    @DeleteMapping("/{id}")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        deptMapper.lockAllDeptIds();
        if (deptService.getById(id) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "部门不存在");
        }
        // 检查是否有子部门
        long childCount = deptService.count(
                new LambdaQueryWrapper<SysDeptEntity>()
                        .eq(SysDeptEntity::getParentId, id)
                        .eq(SysDeptEntity::getDelFlag, 0));
        if (childCount > 0) {
            throw new BusinessException("存在子部门，无法删除");
        }
        Long userCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeptId, id)
                .eq(SysUserEntity::getDelFlag, (byte) 0));
        if (userCount != null && userCount > 0) {
            throw new BusinessException("部门下仍有用户，无法删除");
        }
        deptService.removeById(id);
        log.info("删除部门: deptId={}", id);
        return Result.success();
    }

    /**
     * 构建部门树
     */
    private List<DeptTreeVO> buildDeptTree(List<SysDeptEntity> depts, Long parentId,
            Map<Long, Integer> directUserCountMap, Set<Long> visited) {
        return depts.stream()
                .filter(d -> parentId.equals(d.getParentId()) && d.getId() != null && !visited.contains(d.getId()))
                .map(d -> {
                    visited.add(d.getId());
                    DeptTreeVO vo = new DeptTreeVO();
                    vo.setId(d.getId());
                    vo.setParentId(d.getParentId());
                    vo.setDeptName(d.getDeptName());
                    vo.setPhone(d.getPhone());
                    vo.setEmail(d.getEmail());
                    vo.setLeaderId(d.getLeaderId());
                    vo.setSortOrder(d.getSortOrder());
                    vo.setStatus(d.getStatus() != null ? d.getStatus().intValue() : 1);
                    vo.setCreateTime(d.getCreateTime());

                    List<DeptTreeVO> children = buildDeptTree(depts, d.getId(), directUserCountMap, visited);
                    vo.setChildren(children);

                    int selfCount = directUserCountMap.getOrDefault(d.getId(), 0);
                    int childTotal = children.stream()
                            .mapToInt(child -> child.getUserCount() == null ? 0 : child.getUserCount()).sum();
                    vo.setUserCount(selfCount + childTotal);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 统计每个部门的直属用户数（del_flag=0），用于部门树 userCount。
     */
    private Map<Long, Integer> buildDeptUserCountMap() {
        QueryWrapper<SysUserEntity> wrapper = new QueryWrapper<SysUserEntity>()
                .select("dept_id", "COUNT(1) AS cnt")
                .eq("del_flag", 0)
                .isNotNull("dept_id")
                .groupBy("dept_id");

        List<Map<String, Object>> rows = sysUserMapper.selectMaps(wrapper);

        Map<Long, Integer> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object deptIdObj = row.get("dept_id");
            Object cntObj = row.get("cnt");
            if (deptIdObj == null) {
                continue;
            }
            Long deptId = deptIdObj instanceof Number ? ((Number) deptIdObj).longValue()
                    : Long.parseLong(deptIdObj.toString());
            int cnt = cntObj instanceof Number ? ((Number) cntObj).intValue() : Integer.parseInt(cntObj.toString());
            map.put(deptId, cnt);
        }
        return map;
    }

    private void applyDeptFields(SysDeptEntity dept, DeptUpsertVO vo) {
        if (vo.getDeptName() != null) {
            dept.setDeptName(vo.getDeptName().trim());
        }
        if (vo.getLeaderId() != null) {
            dept.setLeaderId(vo.getLeaderId());
        }
        if (vo.getPhone() != null) {
            dept.setPhone(vo.getPhone());
        }
        if (vo.getEmail() != null) {
            dept.setEmail(vo.getEmail());
        }
        if (vo.getSortOrder() != null) {
            dept.setSortOrder(vo.getSortOrder());
        }
        if (vo.getStatus() != null) {
            dept.setStatus(vo.getStatus().byteValue());
        }
    }

    private void validateDeptName(String deptName, boolean required) {
        if (deptName == null) {
            if (required) {
                throw new BusinessException("部门名称不能为空");
            }
            return;
        }
        if (deptName.isBlank()) {
            throw new BusinessException("部门名称不能为空");
        }
    }

    private void validateParentExists(Long parentId) {
        if (parentId != null && parentId != 0L && deptService.getById(parentId) == null) {
            throw new com.permacore.iam.security.handler.BusinessException("上级部门不存在: " + parentId);
        }
    }

    private void validateActiveParentChain(Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        Map<Long, SysDeptEntity> deptMap = deptService.list(
                        new LambdaQueryWrapper<SysDeptEntity>().eq(SysDeptEntity::getDelFlag, (byte) 0))
                .stream().collect(Collectors.toMap(SysDeptEntity::getId, dept -> dept, (a, b) -> a));
        Set<Long> visited = new HashSet<>();
        Long cursor = parentId;
        while (cursor != null && cursor != 0L && visited.add(cursor)) {
            SysDeptEntity ancestor = deptMap.get(cursor);
            if (ancestor == null || !Byte.valueOf((byte) 1).equals(ancestor.getStatus())) {
                throw new BusinessException("启用部门不能位于已停用的上级部门下");
            }
            cursor = ancestor.getParentId();
        }
        if (cursor != null && cursor != 0L) {
            throw new BusinessException("部门树中存在环，请先修复数据");
        }
    }

    private void validateCanDisable(Long deptId) {
        long activeChildCount = deptService.count(new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getParentId, deptId)
                .eq(SysDeptEntity::getStatus, (byte) 1)
                .eq(SysDeptEntity::getDelFlag, (byte) 0));
        Long activeUserCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUserEntity>()
                .eq(SysUserEntity::getDeptId, deptId)
                .eq(SysUserEntity::getStatus, (byte) 1)
                .eq(SysUserEntity::getDelFlag, (byte) 0));
        if (activeChildCount > 0 || (activeUserCount != null && activeUserCount > 0)) {
            throw new BusinessException("部门仍有启用的子部门或用户，不能停用");
        }
    }

    private boolean wouldCreateCycle(Long deptId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return false;
        }
        Map<Long, Long> parents = deptService.list(
                        new LambdaQueryWrapper<SysDeptEntity>().eq(SysDeptEntity::getDelFlag, (byte) 0))
                .stream()
                .collect(Collectors.toMap(SysDeptEntity::getId, SysDeptEntity::getParentId, (a, b) -> a));
        Set<Long> visited = new HashSet<>();
        Long current = parentId;
        while (current != null && current != 0L && visited.add(current)) {
            if (deptId.equals(current)) {
                return true;
            }
            current = parents.get(current);
        }
        return current != null && current != 0L;
    }
}
