package com.painuo.suite.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 活动、场次、报名相关出入参。 */
public final class ActivityDtos {

    private ActivityDtos() {
    }

    /** 新增/编辑活动。 */
    public record ActivitySaveRequest(
            @NotBlank(message = "活动标题不能为空")
            @Size(max = 150, message = "活动标题最长 150 字")
            String title,

            @Size(max = 500, message = "封面地址过长")
            String coverUrl,

            Long regionId,

            @Size(max = 2000, message = "活动描述最长 2000 字")
            String description,

            /** 1 上线 / 0 下线。 */
            Integer status,

            LocalDateTime signupStartTime,

            LocalDateTime signupEndTime,

            LocalDateTime startTime,

            LocalDateTime endTime
    ) {
    }

    /** 新增/编辑场次。容量变更走乐观锁，避免两个运营同时改覆盖彼此。 */
    public record SessionSaveRequest(
            @NotBlank(message = "场次名称不能为空")
            @Size(max = 100, message = "场次名称最长 100 字")
            String name,

            LocalDateTime startTime,

            LocalDateTime endTime,

            @NotNull(message = "总名额不能为空")
            @Min(value = 1, message = "总名额至少 1")
            @Max(value = 100000, message = "总名额过大")
            Integer capacity,

            BigDecimal price
    ) {
    }

    /** 场次视图。 */
    public record SessionVO(
            Long id,
            Long activityId,
            String name,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer capacity,
            Integer remain,
            BigDecimal price,
            boolean soldOut
    ) {
    }

    /** 活动视图（带场次）。 */
    public record ActivityVO(
            Long id,
            String title,
            String coverUrl,
            Long regionId,
            String description,
            Integer status,
            LocalDateTime signupStartTime,
            LocalDateTime signupEndTime,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean signupOpen,
            List<SessionVO> sessions
    ) {
    }

    /** 报名。 */
    public record SignupRequest(
            @NotNull(message = "活动不能为空")
            Long activityId,

            @NotNull(message = "场次不能为空")
            Long sessionId,

            @NotBlank(message = "联系人不能为空")
            @Size(max = 50, message = "联系人最长 50 字")
            String contactName,

            @NotBlank(message = "手机号不能为空")
            @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
            String contactPhone
    ) {
    }

    /** 报名视图（小程序"我的报名"直接用）。 */
    public record SignupVO(
            Long id,
            Long activityId,
            String activityTitle,
            Long sessionId,
            String sessionName,
            LocalDateTime sessionStartTime,
            String code,
            Integer status,
            String statusText,
            String contactName,
            String contactPhone,
            LocalDateTime signupTime,
            LocalDateTime verifyTime
    ) {
    }

    /** 核销。 */
    public record VerifyRequest(
            @NotBlank(message = "核销码不能为空")
            String code
    ) {
    }

    /** 核销结果。 */
    public record VerifyVO(
            Long signupId,
            String activityTitle,
            String contactName,
            String contactPhone,
            Integer status,
            String statusText,
            LocalDateTime verifyTime
    ) {
    }
}
