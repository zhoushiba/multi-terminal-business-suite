package com.painuo.suite.wechat;

/**
 * code2session 结果。
 *
 * @param openId     用户在本小程序内的唯一标识
 * @param sessionKey 会话密钥（解密手机号用得到；本项目不下发到前端）
 * @param unionId    同主体多应用打通标识，可能为空
 * @param mock       是否为本地 mock 模式（前端/日志需要区分，避免把假 openid 当成真实用户）
 */
public record WechatSession(
        String openId,
        String sessionKey,
        String unionId,
        boolean mock
) {
}
