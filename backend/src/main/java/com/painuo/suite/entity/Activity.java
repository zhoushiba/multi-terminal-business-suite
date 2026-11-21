package com.painuo.suite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 活动（一个活动下可有多个场次，名额挂在场次上）。 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String coverUrl;

    private Long regionId;

    private String description;

    /** 1 上线 / 0 下线。 */
    private Integer status;

    private LocalDateTime signupStartTime;

    private LocalDateTime signupEndTime;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
