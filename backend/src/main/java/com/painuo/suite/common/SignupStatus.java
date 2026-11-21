package com.painuo.suite.common;

/**
 * 报名状态机：已报名(1) → 已核销(2) / 已取消(3)。
 *
 * <p>取消后可以重新报名，所以唯一索引是 (session_id, user_id, status) 而不是 (session_id, user_id)。</p>
 */
public enum SignupStatus {

    SIGNED(1, "已报名"),
    VERIFIED(2, "已核销"),
    CANCELLED(3, "已取消");

    private final int code;
    private final String label;

    SignupStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static String labelOf(Integer code) {
        if (code == null) {
            return "未知";
        }
        for (SignupStatus status : values()) {
            if (status.code == code) {
                return status.label;
            }
        }
        return "未知";
    }
}
