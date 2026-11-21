package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 活动场次（名额载体）。
 *
 * <p><b>并发核心</b>：{@code remain} 是热点字段。扣减走
 * {@code UPDATE activity_session SET remain = remain - 1 WHERE id = ? AND remain > 0}，
 * 由数据库行锁保证原子性，返回影响行数为 0 即"已售罄"，无需先查后改（避免 TOCTOU）。</p>
 *
 * <p>{@code @Version} 作为第二道保险：后台修改容量等场景走乐观锁，
 * 两个运营同时编辑不会互相覆盖。</p>
 */
@Data
@TableName("activity_session")
public class ActivitySession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long activityId;

    private String name;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 总名额。 */
    private Integer capacity;

    /** 剩余名额，不允许为负——由 SQL 条件 + 数据库约束双重保证。 */
    private Integer remain;

    private BigDecimal price;

    @Version
    private Integer version;

    private LocalDateTime createdAt;
}
