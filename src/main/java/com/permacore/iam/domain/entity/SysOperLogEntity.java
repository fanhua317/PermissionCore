package com.permacore.iam.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
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
@ApiModel(value = "SysOperLogEntity对象", description = "操作日志表")
public class SysOperLogEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("日志ID")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty("操作标题")
    @TableField("title")
    private String title;

    @ApiModelProperty("业务类型（0-其他 1-新增 2-修改 3-删除）")
    @TableField("business_type")
    private Byte businessType;

    @ApiModelProperty("请求方法")
    @TableField("method")
    private String method;

    @ApiModelProperty("请求方式（GET/POST）")
    @TableField("request_method")
    private String requestMethod;

    @ApiModelProperty("操作人ID")
    @TableField("operator_id")
    private Long operatorId;

    @ApiModelProperty("操作人姓名")
    @TableField("operator_name")
    private String operatorName;

    @ApiModelProperty("操作IP")
    @TableField("oper_ip")
    private String operIp;

    @ApiModelProperty("操作地点")
    @TableField("oper_location")
    private String operLocation;

    @ApiModelProperty("请求参数")
    @TableField("oper_param")
    private String operParam;

    @ApiModelProperty("返回结果")
    @TableField("json_result")
    private String jsonResult;

    @ApiModelProperty("操作状态（0-正常 1-异常）")
    @TableField("status")
    private Byte status;

    @ApiModelProperty("错误消息")
    @TableField("error_msg")
    private String errorMsg;

    @ApiModelProperty("操作时间")
    @TableField("oper_time")
    private LocalDateTime operTime;

    @ApiModelProperty("耗时（毫秒）")
    @TableField("cost_time")
    private Long costTime;
}
