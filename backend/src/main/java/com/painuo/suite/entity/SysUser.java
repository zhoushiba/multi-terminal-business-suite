package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 系统用户（运营后台 + 小程序共用一张表，用 source 区分注册来源）。 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录名，唯一。小程序用户为 mock openid 派生。 */
    private String username;

    /** BCrypt 加密后的密码；小程序用户无密码。 */
    private String password;

    private String nickname;

    private String avatar;

    private String phone;

    /** 1 启用 / 0 禁用。 */
    private Integer status;

    /** 注册来源：web / miniapp。 */
    private String source;

    /** 小程序 openid，用于 code2session 登录。 */
    private String openid;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
