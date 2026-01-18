package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 操作日志表
 * </p>
 *
 * @author PermaCore团队
 * @since 2025-12-11
 */
@Getter
@Setter
@TableName("sys_oper_log")
@Schema(name = "SysOperLogEntity", description = "操作日志表")
public class SysOperLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "操作标题")
    @TableField("title")
    private String title;

    @Schema(description = "业务类型（0-其他 1-新增 2-修改 3-删除）")
    @TableField("business_type")
    private Byte businessType;

    @Schema(description = "请求方法")
    @TableField("method")
    private String method;

    @Schema(description = "请求方式（GET/POST）")
    @TableField("request_method")
    private String requestMethod;

    @Schema(description = "操作人ID")
    @TableField("operator_id")
    private Long operatorId;

    @Schema(description = "操作人姓名")
    @TableField("operator_name")
    private String operatorName;

    @Schema(description = "操作IP")
    @TableField("oper_ip")
    private String operIp;

    @Schema(description = "操作地点")
    @TableField("oper_location")
    private String operLocation;

    @Schema(description = "请求参数")
    @TableField("oper_param")
    private String operParam;

    @Schema(description = "返回结果")
    @TableField("json_result")
    private String jsonResult;

    @Schema(description = "操作状态（0-正常 1-异常）")
    @TableField("status")
    private Byte status;

    @Schema(description = "错误消息")
    @TableField("error_msg")
    private String errorMsg;

    @Schema(description = "操作时间")
    @TableField("oper_time")
    private LocalDateTime operTime;

    @Schema(description = "耗时（毫秒）")
    @TableField("cost_time")
    private Long costTime;
}
