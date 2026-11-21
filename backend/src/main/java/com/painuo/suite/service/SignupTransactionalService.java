package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.common.SignupStatus;
import com.painuo.suite.dto.ActivityDtos;
import com.painuo.suite.entity.Activity;
import com.painuo.suite.entity.ActivitySession;
import com.painuo.suite.entity.ActivitySignup;
import com.painuo.suite.entity.SignupVerifyLog;
import com.painuo.suite.mapper.ActivityMapper;
import com.painuo.suite.mapper.ActivitySessionMapper;
import com.painuo.suite.mapper.ActivitySignupMapper;
import com.painuo.suite.mapper.SignupVerifyLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 报名/核销的<b>事务边界</b>。
 *
 * <p>为什么单独抽一个 Bean？因为 Spring 的 {@code @Transactional} 依赖代理，
 * 同一个类里自调用不生效——把重试逻辑放在外层 {@link SignupService}，
 * 事务方法放在这里，每次重试都是一次全新的、真正被代理的事务。</p>
 */
@Service
public class SignupTransactionalService {

    private static final Logger log = LoggerFactory.getLogger(SignupTransactionalService.class);

    private final ActivityMapper activityMapper;
    private final ActivitySessionMapper sessionMapper;
    private final ActivitySignupMapper signupMapper;
    private final SignupVerifyLogMapper verifyLogMapper;

    public SignupTransactionalService(ActivityMapper activityMapper,
                                      ActivitySessionMapper sessionMapper,
                                      ActivitySignupMapper signupMapper,
                                      SignupVerifyLogMapper verifyLogMapper) {
        this.activityMapper = activityMapper;
        this.sessionMapper = sessionMapper;
        this.signupMapper = signupMapper;
        this.verifyLogMapper = verifyLogMapper;
    }

    /**
     * 抢名额。
     *
     * <p>核心是这一条 SQL（{@code ActivitySessionMapper#deductRemain}）：</p>
     * <pre>
     *   UPDATE activity_session SET remain = remain - 1 WHERE id = ? AND remain &gt; 0
     * </pre>
     * <p>它把"判断 + 扣减"合成一个原子操作，返回 0 行即表示名额已售罄。
     * 相比"先 SELECT remain 再 if 判断再 UPDATE"，不会出现两个事务都读到 remain=1
     * 然后双双扣成 -1 的经典超卖问题；比悲观锁 {@code SELECT ... FOR UPDATE} 少一次往返，
     * 也不需要业务层手动加锁。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public ActivityDtos.SignupVO doSignup(Long userId, ActivityDtos.SignupRequest request) {
        Activity activity = activityMapper.selectById(request.activityId());
        if (activity == null || activity.getStatus() == null || activity.getStatus() != 1) {
            throw BizException.notFound("活动");
        }
        ActivitySession session = sessionMapper.selectById(request.sessionId());
        if (session == null || !request.activityId().equals(session.getActivityId())) {
            throw BizException.param("场次不属于该活动");
        }

        LocalDateTime now = LocalDateTime.now();
        if (activity.getSignupStartTime() != null && now.isBefore(activity.getSignupStartTime())) {
            throw BizException.of(ResultCode.SIGNUP_EXPIRED, "报名尚未开始");
        }
        if (activity.getSignupEndTime() != null && now.isAfter(activity.getSignupEndTime())) {
            throw BizException.of(ResultCode.SIGNUP_EXPIRED, "报名已结束");
        }
        if (session.getStartTime() != null && now.isAfter(session.getStartTime())) {
            throw BizException.of(ResultCode.SIGNUP_EXPIRED, "该场次已开始，无法报名");
        }

        // 重复报名前置校验：正常情况下由它拦下，唯一索引作为最后一道防线兜住并发
        Long duplicated = signupMapper.selectCount(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getSessionId, request.sessionId())
                .eq(ActivitySignup::getUserId, userId)
                .eq(ActivitySignup::getStatus, SignupStatus.SIGNED.code()));
        if (duplicated != null && duplicated > 0) {
            throw BizException.of(ResultCode.SIGNUP_DUPLICATE, "你已报名该场次");
        }

        int affected = sessionMapper.deductRemain(request.sessionId());
        if (affected == 0) {
            throw BizException.of(ResultCode.SIGNUP_SOLD_OUT, "手慢了，该场次名额已满");
        }

        ActivitySignup signup = new ActivitySignup();
        signup.setSessionId(request.sessionId());
        signup.setActivityId(request.activityId());
        signup.setUserId(userId);
        signup.setContactName(request.contactName());
        signup.setContactPhone(request.contactPhone());
        signup.setCode(generateCode());
        signup.setStatus(SignupStatus.SIGNED.code());
        signup.setSignupTime(now);
        signupMapper.insert(signup);

        log.info("报名成功: userId={}, sessionId={}, signupId={}, 剩余名额={}",
                userId, request.sessionId(), signup.getId(), session.getRemain() == null ? -1 : session.getRemain() - 1);
        return toVO(signup, activity.getTitle(), session);
    }

    /** 取消报名：状态置为已取消并把名额还回去（同一事务，要么都成功要么都不动）。 */
    @Transactional(rollbackFor = Exception.class)
    public void doCancel(Long userId, Long signupId) {
        ActivitySignup signup = signupMapper.selectById(signupId);
        if (signup == null || !userId.equals(signup.getUserId())) {
            throw BizException.notFound("报名记录");
        }
        if (signup.getStatus() == null || signup.getStatus() != SignupStatus.SIGNED.code()) {
            throw BizException.of(ResultCode.SIGNUP_STATE_ILLEGAL,
                    "当前状态（" + SignupStatus.labelOf(signup.getStatus()) + "）无法取消");
        }
        ActivitySignup patch = new ActivitySignup();
        patch.setId(signupId);
        patch.setStatus(SignupStatus.CANCELLED.code());
        signupMapper.updateById(patch);
        sessionMapper.restoreRemain(signup.getSessionId());
        log.info("取消报名: signupId={}, sessionId={}", signupId, signup.getSessionId());
    }

    /** 后台核销：核销码 → 已报名改为已核销，并写核销日志。 */
    @Transactional(rollbackFor = Exception.class)
    public ActivityDtos.VerifyVO doVerify(Long operatorId, String code) {
        ActivitySignup signup = signupMapper.selectByCode(code.trim());
        if (signup == null) {
            throw BizException.notFound("核销码");
        }
        if (signup.getStatus() == null || signup.getStatus() != SignupStatus.SIGNED.code()) {
            throw BizException.of(ResultCode.SIGNUP_STATE_ILLEGAL,
                    "该报名当前状态为「" + SignupStatus.labelOf(signup.getStatus()) + "」，不可核销");
        }
        LocalDateTime now = LocalDateTime.now();
        ActivitySignup patch = new ActivitySignup();
        patch.setId(signup.getId());
        patch.setStatus(SignupStatus.VERIFIED.code());
        patch.setVerifyTime(now);
        patch.setVerifyBy(operatorId);
        signupMapper.updateById(patch);

        SignupVerifyLog verifyLog = new SignupVerifyLog();
        verifyLog.setSignupId(signup.getId());
        verifyLog.setOperatorId(operatorId);
        verifyLog.setAction("verify");
        verifyLog.setResult("success");
        verifyLogMapper.insert(verifyLog);

        Activity activity = activityMapper.selectById(signup.getActivityId());
        return new ActivityDtos.VerifyVO(signup.getId(),
                activity == null ? null : activity.getTitle(),
                signup.getContactName(), signup.getContactPhone(),
                SignupStatus.VERIFIED.code(), SignupStatus.VERIFIED.label(), now);
    }

    private ActivityDtos.SignupVO toVO(ActivitySignup signup, String activityTitle, ActivitySession session) {
        return new ActivityDtos.SignupVO(
                signup.getId(), signup.getActivityId(), activityTitle,
                signup.getSessionId(), session == null ? null : session.getName(),
                session == null ? null : session.getStartTime(),
                signup.getCode(), signup.getStatus(), SignupStatus.labelOf(signup.getStatus()),
                signup.getContactName(), signup.getContactPhone(),
                signup.getSignupTime(), signup.getVerifyTime());
    }

    /** 核销码：SG + 12 位十六进制，碰撞概率可忽略且肉眼可读。 */
    static String generateCode() {
        return "SG" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
