package com.painuo.suite.service;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.common.SignupStatus;
import com.painuo.suite.dto.ActivityDtos;
import com.painuo.suite.entity.Activity;
import com.painuo.suite.entity.ActivitySession;
import com.painuo.suite.entity.ActivitySignup;
import com.painuo.suite.entity.SignupVerifyLog;
import com.painuo.suite.entity.SysUser;
import com.painuo.suite.mapper.ActivityMapper;
import com.painuo.suite.mapper.ActivitySessionMapper;
import com.painuo.suite.mapper.ActivitySignupMapper;
import com.painuo.suite.mapper.SignupVerifyLogMapper;
import com.painuo.suite.mapper.SysUserMapper;
import com.painuo.suite.security.AuthContext;
import com.painuo.suite.security.AuthPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * 报名名额并发扣减测试 —— 本项目的核心能力点。
 *
 * <p>扣减靠一条原子 SQL 完成：{@code UPDATE activity_session SET remain = remain - 1
 * WHERE id = ? AND remain > 0}。它把"判断余量"和"扣减"合成一次写操作，
 * 返回 0 行就代表售罄，因此不存在"读到 remain=1 的两个事务都扣成 -1"的超卖窗口。</p>
 *
 * <p>测试策略：容量 5、并发 16 个线程同时抢，断言成功的恰好 5 个、remain 恰好 0、
 * 库里的有效报名恰好 5 条、且换了 5 个不同用户。这是"不超卖"最直接的可验证证据。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("报名名额并发扣减")
class SignupConcurrencyTest {

    private static final int CAPACITY = 5;
    private static final int CONCURRENT_THREADS = 16;

    @Autowired
    private SignupService signupService;

    @Autowired
    private SignupTransactionalService signupTx;

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private ActivitySessionMapper sessionMapper;

    @Autowired
    private ActivitySignupMapper signupMapper;

    @Autowired
    private SignupVerifyLogMapper verifyLogMapper;

    @Autowired
    private SysUserMapper userMapper;

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    @DisplayName("16 个线程抢 5 个名额：恰好 5 人成功，剩余名额归零，绝不超卖")
    void concurrentSignupNeverOversells() throws Exception {
        Activity activity = createActivity();
        ActivitySession session = createSession(activity.getId(), CAPACITY);
        List<Long> userIds = createUsers(CONCURRENT_THREADS);

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch finishGate = new CountDownLatch(CONCURRENT_THREADS);
        AtomicInteger success = new AtomicInteger();
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        ConcurrentLinkedQueue<Long> successUsers = new ConcurrentLinkedQueue<>();

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        try {
            for (Long userId : userIds) {
                pool.submit(() -> {
                    try {
                        startGate.await();
                        AuthContext.set(new AuthPrincipal(userId, "concurrent-" + userId, "并发用户",
                                List.of("member"), List.of(), null));
                        signupService.signup(new ActivityDtos.SignupRequest(
                                activity.getId(), session.getId(), "并发用户" + userId, "1390000" + (1000 + userId % 9000)));
                        success.incrementAndGet();
                        successUsers.add(userId);
                    } catch (BizException ex) {
                        failures.add(ex.resultCode().name());
                    } catch (Exception ex) {
                        failures.add(ex.getClass().getSimpleName() + ":" + ex.getMessage());
                    } finally {
                        AuthContext.clear();
                        finishGate.countDown();
                    }
                });
            }
            startGate.countDown();
            assertThat(finishGate.await(60, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        ActivitySession reloaded = sessionMapper.selectById(session.getId());
        long activeSignups = signupMapper.countActiveBySession(session.getId());
        Set<Long> distinctUsers = successUsers.stream().collect(Collectors.toSet());

        // 核心断言：成功数 == 容量，且余量精确归零（不会变成 -1）
        assertThat(success.get()).isEqualTo(CAPACITY);
        assertThat(reloaded.getRemain()).isZero();
        assertThat(activeSignups).isEqualTo(CAPACITY);
        assertThat(distinctUsers).hasSize(CAPACITY);
        // 其余请求必须是因为"售罄"被拒，而不是因为系统异常
        assertThat(failures).hasSize(CONCURRENT_THREADS - CAPACITY);
        assertThat(failures).allMatch(ResultCode.SIGNUP_SOLD_OUT.name()::equals,
                "所有失败都应是名额售罄（SIGNUP_SOLD_OUT），实际: " + failures);
    }

    @Test
    @DisplayName("同一用户重复报名被拒（SIGNUP_DUPLICATE）")
    void duplicateSignupRejected() {
        Activity activity = createActivity();
        ActivitySession session = createSession(activity.getId(), 3);
        Long userId = createUsers(1).get(0);
        AuthContext.set(principal(userId));

        signupService.signup(request(activity.getId(), session.getId()));
        BizException ex = catchThrowableOfType(
                () -> signupService.signup(request(activity.getId(), session.getId())), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.SIGNUP_DUPLICATE);
        // 重复报名不应多扣名额
        assertThat(sessionMapper.selectById(session.getId()).getRemain()).isEqualTo(2);
    }

    @Test
    @DisplayName("容量 1 的场次：第二个用户拿到售罄错误而不是扣成负数")
    void soldOutRejected() {
        Activity activity = createActivity();
        ActivitySession session = createSession(activity.getId(), 1);
        List<Long> userIds = createUsers(2);

        AuthContext.set(principal(userIds.get(0)));
        signupService.signup(request(activity.getId(), session.getId()));

        AuthContext.set(principal(userIds.get(1)));
        BizException ex = catchThrowableOfType(
                () -> signupService.signup(request(activity.getId(), session.getId())), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.SIGNUP_SOLD_OUT);
        assertThat(sessionMapper.selectById(session.getId()).getRemain()).isZero();
    }

    @Test
    @DisplayName("取消报名把名额还回去，状态变为已取消")
    void cancelRestoresSeat() {
        Activity activity = createActivity();
        ActivitySession session = createSession(activity.getId(), 2);
        Long userId = createUsers(1).get(0);
        AuthContext.set(principal(userId));

        ActivityDtos.SignupVO signup = signupService.signup(request(activity.getId(), session.getId()));
        assertThat(sessionMapper.selectById(session.getId()).getRemain()).isEqualTo(1);

        signupService.cancel(signup.id());

        assertThat(sessionMapper.selectById(session.getId()).getRemain()).isEqualTo(2);
        ActivitySignup cancelled = signupMapper.selectById(signup.id());
        assertThat(cancelled.getStatus()).isEqualTo(SignupStatus.CANCELLED.code());
    }

    @Test
    @DisplayName("核销：状态置为已核销并写入核销日志，重复核销被拒")
    void verifyWritesLogAndRejectsSecondTime() {
        Activity activity = createActivity();
        ActivitySession session = createSession(activity.getId(), 2);
        List<Long> userIds = createUsers(2);

        AuthContext.set(principal(userIds.get(0)));
        ActivityDtos.SignupVO signup = signupService.signup(request(activity.getId(), session.getId()));

        AuthContext.set(principal(userIds.get(1)));
        ActivityDtos.VerifyVO verified = signupService.verify(signup.code());
        assertThat(verified.status()).isEqualTo(SignupStatus.VERIFIED.code());
        assertThat(verified.verifyTime()).isNotNull();

        List<SignupVerifyLog> logs = verifyLogMapper.selectList(null);
        assertThat(logs).anyMatch(log -> signup.id().equals(log.getSignupId())
                && "verify".equals(log.getAction()) && "success".equals(log.getResult()));

        BizException ex = catchThrowableOfType(() -> signupService.verify(signup.code()), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.SIGNUP_STATE_ILLEGAL);
    }

    @Test
    @DisplayName("核销不存在的核销码返回 404")
    void verifyUnknownCodeReturnsNotFound() {
        Activity activity = createActivity();
        createSession(activity.getId(), 2);
        Long userId = createUsers(1).get(0);
        AuthContext.set(principal(userId));

        BizException ex = catchThrowableOfType(
                () -> signupService.verify("SG-NOT-EXISTS-" + UUID.randomUUID()), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.NOT_FOUND);
    }

    @Test
    @DisplayName("活动下线后不可报名（404）")
    void offlineActivityCannotBeSignedUp() {
        Activity activity = createActivity();
        ActivitySession session = createSession(activity.getId(), 2);
        Activity offline = new Activity();
        offline.setId(activity.getId());
        offline.setStatus(0);
        activityMapper.updateById(offline);

        Long userId = createUsers(1).get(0);
        AuthContext.set(principal(userId));
        BizException ex = catchThrowableOfType(
                () -> signupService.signup(request(activity.getId(), session.getId())), BizException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.resultCode()).isEqualTo(ResultCode.NOT_FOUND);
    }

    // ------------------------------------------------------------------ 测试数据构造

    private Activity createActivity() {
        Activity activity = new Activity();
        activity.setTitle("并发测试活动-" + UUID.randomUUID().toString().substring(0, 8));
        activity.setStatus(1);
        activity.setRegionId(1L);
        activity.setSignupStartTime(LocalDateTime.now().minusDays(1));
        activity.setSignupEndTime(LocalDateTime.now().plusDays(10));
        activity.setStartTime(LocalDateTime.now().plusDays(11));
        activity.setEndTime(LocalDateTime.now().plusDays(11).plusHours(2));
        activityMapper.insert(activity);
        return activity;
    }

    private ActivitySession createSession(Long activityId, int capacity) {
        ActivitySession session = new ActivitySession();
        session.setActivityId(activityId);
        session.setName("测试场次-" + capacity);
        session.setStartTime(LocalDateTime.now().plusDays(11));
        session.setEndTime(LocalDateTime.now().plusDays(11).plusHours(2));
        session.setCapacity(capacity);
        session.setRemain(capacity);
        session.setPrice(BigDecimal.ZERO);
        session.setVersion(0);
        sessionMapper.insert(session);
        return session;
    }

    private List<Long> createUsers(int count) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return java.util.stream.IntStream.range(0, count).mapToObj(index -> {
            SysUser user = new SysUser();
            user.setUsername("conc-" + suffix + "-" + index);
            user.setPassword("pbkdf2$120000$00$00");
            user.setNickname("并发用户" + index);
            user.setStatus(1);
            user.setSource("miniapp");
            userMapper.insert(user);
            return user.getId();
        }).toList();
    }

    private ActivityDtos.SignupRequest request(Long activityId, Long sessionId) {
        return new ActivityDtos.SignupRequest(activityId, sessionId, "测试联系人", "13900001111");
    }

    private AuthPrincipal principal(Long userId) {
        return new AuthPrincipal(userId, "user-" + userId, "用户" + userId, List.of("member"), List.of(), null);
    }
}
