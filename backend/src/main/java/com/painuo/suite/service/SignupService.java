package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.common.SignupStatus;
import com.painuo.suite.dto.ActivityDtos;
import com.painuo.suite.entity.Activity;
import com.painuo.suite.entity.ActivitySession;
import com.painuo.suite.entity.ActivitySignup;
import com.painuo.suite.mapper.ActivityMapper;
import com.painuo.suite.mapper.ActivitySessionMapper;
import com.painuo.suite.mapper.ActivitySignupMapper;
import com.painuo.suite.security.AuthContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报名对外门面：负责并发重试、列表组装。
 *
 * <p><b>为什么需要重试？</b>名额扣减是 {@code UPDATE ... WHERE remain > 0} 的悲观写，
 * MySQL InnoDB 下并发事务会排队等行锁；H2（单测环境）的 MVStore 在同一行被并发更新时
 * 会直接抛 "Concurrent update" 异常而不是排队。为了让两种数据库的语义一致、
 * 单测结论对生产有意义，这里对并发失败做有限次重试（每次都开新事务）。
 * 这不是给业务擦屁股——真正的失败分支（名额售罄）不会重试，因为它返回的是 0 行而不是异常。</p>
 */
@Service
public class SignupService {

    private static final Logger log = LoggerFactory.getLogger(SignupService.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 20;

    private final SignupTransactionalService signupTx;
    private final ActivitySignupMapper signupMapper;
    private final ActivitySessionMapper sessionMapper;
    private final ActivityMapper activityMapper;

    public SignupService(SignupTransactionalService signupTx,
                         ActivitySignupMapper signupMapper,
                         ActivitySessionMapper sessionMapper,
                         ActivityMapper activityMapper) {
        this.signupTx = signupTx;
        this.signupMapper = signupMapper;
        this.sessionMapper = sessionMapper;
        this.activityMapper = activityMapper;
    }

    /** 报名（含并发重试）。 */
    public ActivityDtos.SignupVO signup(ActivityDtos.SignupRequest request) {
        Long userId = AuthContext.requireUserId();
        for (int attempt = 1; ; attempt++) {
            try {
                return signupTx.doSignup(userId, request);
            } catch (ConcurrencyFailureException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("报名并发冲突重试 {} 次仍失败: userId={}, sessionId={}",
                            MAX_ATTEMPTS, userId, request.sessionId());
                    throw BizException.of(ResultCode.CONFLICT, "当前报名人数较多，请稍后重试");
                }
                log.debug("报名遇到并发冲突，第 {} 次重试", attempt);
                sleep(RETRY_BACKOFF_MILLIS * attempt);
            }
        }
    }

    /** 取消报名。 */
    public void cancel(Long signupId) {
        signupTx.doCancel(AuthContext.requireUserId(), signupId);
    }

    /** 核销（运营后台扫码/输码）。 */
    public ActivityDtos.VerifyVO verify(String code) {
        return signupTx.doVerify(AuthContext.requireUserId(), code);
    }

    /** 我的报名（小程序"我的"页）。 */
    public PageResult<ActivityDtos.SignupVO> mySignups(Integer status, PageQuery pageQuery) {
        LambdaQueryWrapper<ActivitySignup> wrapper = Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getUserId, AuthContext.requireUserId())
                .eq(status != null, ActivitySignup::getStatus, status)
                .orderByDesc(ActivitySignup::getId);
        return selectPage(wrapper, pageQuery);
    }

    /** 后台报名列表（可按活动/场次/状态/手机号过滤）。 */
    public PageResult<ActivityDtos.SignupVO> pageForAdmin(Long activityId, Long sessionId, Integer status,
                                                          String keyword, PageQuery pageQuery) {
        LambdaQueryWrapper<ActivitySignup> wrapper = Wrappers.<ActivitySignup>lambdaQuery()
                .eq(activityId != null, ActivitySignup::getActivityId, activityId)
                .eq(sessionId != null, ActivitySignup::getSessionId, sessionId)
                .eq(status != null, ActivitySignup::getStatus, status)
                .and(hasText(keyword), w -> w.like(ActivitySignup::getContactName, keyword)
                        .or().like(ActivitySignup::getContactPhone, keyword)
                        .or().like(ActivitySignup::getCode, keyword))
                .orderByDesc(ActivitySignup::getId);
        return selectPage(wrapper, pageQuery);
    }

    // ------------------------------------------------------------------ 内部方法

    /**
     * 分页 + 批量补全活动名/场次名。
     * 用两次 selectBatchIds 替代逐行查询，避免 N+1。
     */
    private PageResult<ActivityDtos.SignupVO> selectPage(LambdaQueryWrapper<ActivitySignup> wrapper,
                                                         PageQuery pageQuery) {
        Page<ActivitySignup> page = signupMapper.selectPage(pageQuery.toPage(), wrapper);
        List<ActivitySignup> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getCurrent(), page.getSize(), page.getTotal());
        }
        List<Long> sessionIds = records.stream().map(ActivitySignup::getSessionId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> activityIds = records.stream().map(ActivitySignup::getActivityId)
                .filter(Objects::nonNull).distinct().toList();

        Map<Long, ActivitySession> sessions = new HashMap<>();
        if (!sessionIds.isEmpty()) {
            for (ActivitySession session : sessionMapper.selectBatchIds(sessionIds)) {
                sessions.put(session.getId(), session);
            }
        }
        Map<Long, String> titles = new HashMap<>();
        if (!activityIds.isEmpty()) {
            for (Activity activity : activityMapper.selectBatchIds(activityIds)) {
                titles.put(activity.getId(), activity.getTitle());
            }
        }

        List<ActivityDtos.SignupVO> items = records.stream().map(signup -> {
            ActivitySession session = sessions.get(signup.getSessionId());
            return new ActivityDtos.SignupVO(
                    signup.getId(), signup.getActivityId(), titles.get(signup.getActivityId()),
                    signup.getSessionId(), session == null ? null : session.getName(),
                    session == null ? null : session.getStartTime(),
                    signup.getCode(), signup.getStatus(), SignupStatus.labelOf(signup.getStatus()),
                    signup.getContactName(), signup.getContactPhone(),
                    signup.getSignupTime(), signup.getVerifyTime());
        }).toList();
        return PageResult.of(items, page.getCurrent(), page.getSize(), page.getTotal());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
