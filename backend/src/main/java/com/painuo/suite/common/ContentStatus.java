package com.painuo.suite.common;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 内容审核状态机。
 *
 * <p>把"允许的流转"显式建模，而不是在 service 里写一堆 {@code if (status == 1)}。
 * 好处：非法流转只有一个出口（{@link BizException} + {@link ResultCode#CONTENT_STATE_ILLEGAL}），
 * 并且可以被单元测试穷举。</p>
 *
 * <pre>
 *  草稿(0) --提交--> 待审(1) --通过--> 已发布(2) --下架--> 已下架(3)
 *                       |                                   |
 *                       +--驳回--> 已驳回(4) --重新提交--> 待审(1)
 *   已下架(3) --重新上架--> 已发布(2)
 * </pre>
 */
public enum ContentStatus {

    DRAFT(0, "草稿"),
    PENDING(1, "待审核"),
    PUBLISHED(2, "已发布"),
    OFFLINE(3, "已下架"),
    REJECTED(4, "已驳回");

    private static final Map<ContentStatus, Set<ContentStatus>> ALLOWED = Map.of(
            DRAFT, EnumSet.of(PENDING),
            PENDING, EnumSet.of(PUBLISHED, REJECTED),
            PUBLISHED, EnumSet.of(OFFLINE),
            OFFLINE, EnumSet.of(PUBLISHED, PENDING),
            REJECTED, EnumSet.of(PENDING)
    );

    private final int code;
    private final String label;

    ContentStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static ContentStatus of(Integer code) {
        if (code == null) {
            throw BizException.param("内容状态不能为空");
        }
        for (ContentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw BizException.param("未知的内容状态: " + code);
    }

    /** 文案兜底，未知状态返回"未知"。 */
    public static String labelOf(Integer code) {
        if (code == null) {
            return "未知";
        }
        for (ContentStatus status : values()) {
            if (status.code == code) {
                return status.label;
            }
        }
        return "未知";
    }

    public boolean canTransitionTo(ContentStatus target) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(target);
    }

    /**
     * 校验并返回目标状态，非法流转抛 42201。
     *
     * @param target 目标状态
     */
    public ContentStatus requireTransitionTo(ContentStatus target) {
        if (!canTransitionTo(target)) {
            throw BizException.of(ResultCode.CONTENT_STATE_ILLEGAL,
                    "内容状态不允许从「" + label + "」变更为「" + target.label + "」");
        }
        return target;
    }
}
