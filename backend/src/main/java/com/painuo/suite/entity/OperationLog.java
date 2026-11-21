package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志（审计）。
 *
 * <p>与 traceId 打通的写法：前端报错时把 traceId 报给客服，
 * 运维用 traceId 同时查应用日志和这张表，能定位到"谁在什么时候点了什么"。</p>
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String traceId;

    private Long userId;

    private String username;

    /** 业务动作描述，如 发布内容、核销报名。 */
    private String action;

    private String method;

    private String path;

    private String ip;

    private Long costMs;

    private Integer success;

    private String detail;

    private LocalDateTime createdAt;
}
