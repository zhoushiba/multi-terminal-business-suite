package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 报名记录。
 *
 * <p>{@code (session_id, user_id)} 唯一索引 —— 用数据库兜住"并发重复提交"，
 * 而不是靠应用层 {@code exists} 判断（后者在并发下必然漏）。</p>
 *
 * <p>{@code code} 是核销二维码里的短码，现场扫码核销。</p>
 */
@Data
@TableName("activity_signup")
public class ActivitySignup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long activityId;

    private Long userId;

    private String contactName;

    private String contactPhone;

    /** 核销码，形如 SQ + 12 位随机字符。 */
    private String code;

    /** 1 已报名 / 2 已核销 / 3 已取消。 */
    private Integer status;

    private LocalDateTime signupTime;

    private LocalDateTime verifyTime;

    private Long verifyBy;

    private String remark;

    private LocalDateTime createdAt;
}
