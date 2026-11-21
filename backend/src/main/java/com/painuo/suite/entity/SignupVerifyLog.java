package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 核销流水：每次扫码都留痕，便于对账和纠纷追溯。 */
@Data
@TableName("signup_verify_log")
public class SignupVerifyLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long signupId;

    private Long operatorId;

    /** verify 核销 / cancel 取消。 */
    private String action;

    /** success / fail。 */
    private String result;

    private String remark;

    private LocalDateTime createdAt;
}
