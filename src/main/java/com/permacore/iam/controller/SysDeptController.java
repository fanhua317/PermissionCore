package com.permacore.iam.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.permacore.iam.annotation.OperLog;
import com.permacore.iam.domain.entity.SysDeptEntity;
import com.permacore.iam.domain.entity.SysUserEntity;
import com.permacore.iam.domain.vo.DeptTreeVO;
import com.permacore.iam.domain.vo.Result;
import com.permacore.iam.mapper.SysUserMapper;
import com.permacore.iam.service.SysDeptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 获取部门树形结构
     */
    @Operation(summary = "获取部门树", description = "获取部门树形结构及人数统计")
    @GetMapping("/tree")
    public Result<List<DeptTreeVO>> tree() {
        List<SysDeptEntity> allDepts = deptService.list(
            new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getDelFlag, 0)
                .orderByAsc(SysDeptEntity::getSortOrder)
        );

        Map<Long, Integer> directUserCountMap = buildDeptUserCountMap();
        List<DeptTreeVO> tree = buildDeptTree(allDepts, 0L, directUserCountMap);
        return Result.success(tree);
    }

    /**
     * 获取部门详情
     */
    @Operation(summary = "获取部门详情", description = "根据ID获取部门详情")
    @GetMapping("/{id}")
    public Result<SysDeptEntity> getById(@PathVariable Long id) {
        SysDeptEntity dept = deptService.getById(id);
        return Result.success(dept);
    }

    /**
     * 创建部门
     */
    @Operation(summary = "创建部门", description = "新增部门")
    @OperLog(title = "创建部门", businessType = 1)
    @PreAuthorize("hasAuthority('dept:add')")
    @PostMapping
    public Result<Void> create(@RequestBody SysDeptEntity dept) {
        dept.setDelFlag((byte) 0);
        if (dept.getParentId() == null) {
            dept.setParentId(0L);
        }
        deptService.save(dept);
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
    public Result<Void> update(@PathVariable Long id, @RequestBody SysDeptEntity dept) {
        dept.setId(id);
        deptService.updateById(dept);
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
    public Result<Void> delete(@PathVariable Long id) {
        // 检查是否有子部门
        long childCount = deptService.count(
            new LambdaQueryWrapper<SysDeptEntity>()
                .eq(SysDeptEntity::getParentId, id)
                .eq(SysDeptEntity::getDelFlag, 0)
        );
        if (childCount > 0) {
            return Result.error("存在子部门，无法删除");
        }
        deptService.removeById(id);
        log.info("删除部门: deptId={}", id);
        return Result.success();
    }

    /**
     * 构建部门树
     */
    private List<DeptTreeVO> buildDeptTree(List<SysDeptEntity> depts, Long parentId) {
        return buildDeptTree(depts, parentId, new HashMap<>());
    }

    private List<DeptTreeVO> buildDeptTree(List<SysDeptEntity> depts, Long parentId, Map<Long, Integer> directUserCountMap) {
        return depts.stream()
                .filter(d -> parentId.equals(d.getParentId()))
                .map(d -> {
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

                    List<DeptTreeVO> children = buildDeptTree(depts, d.getId(), directUserCountMap);
                    vo.setChildren(children);

                    int selfCount = directUserCountMap.getOrDefault(d.getId(), 0);
                    int childTotal = children.stream().mapToInt(child -> child.getUserCount() == null ? 0 : child.getUserCount()).sum();
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
            Long deptId = deptIdObj instanceof Number ? ((Number) deptIdObj).longValue() : Long.parseLong(deptIdObj.toString());
            int cnt = cntObj instanceof Number ? ((Number) cntObj).intValue() : Integer.parseInt(cntObj.toString());
            map.put(deptId, cnt);
        }
        return map;
    }
}
