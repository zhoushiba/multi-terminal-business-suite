package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.painuo.suite.common.BizException;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.dto.ActivityDtos;
import com.painuo.suite.entity.Activity;
import com.painuo.suite.entity.ActivitySession;
import com.painuo.suite.entity.ActivitySignup;
import com.painuo.suite.mapper.ActivityMapper;
import com.painuo.suite.mapper.ActivitySessionMapper;
import com.painuo.suite.mapper.ActivitySignupMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 活动与场次管理。
 *
 * <p>名额（remain）只能通过 {@link com.painuo.suite.mapper.ActivitySessionMapper#deductRemain}
 * 原子扣减，编辑场次时不允许直接把 remain 改小——运营改容量时按"总名额差值"同步调整剩余，
 * 并受乐观锁 version 保护，两个运营同时改不会互相覆盖。</p>
 */
@Service
public class ActivityService {

    private final ActivityMapper activityMapper;
    private final ActivitySessionMapper sessionMapper;
    private final ActivitySignupMapper signupMapper;

    public ActivityService(ActivityMapper activityMapper,
                           ActivitySessionMapper sessionMapper,
                           ActivitySignupMapper signupMapper) {
        this.activityMapper = activityMapper;
        this.sessionMapper = sessionMapper;
        this.signupMapper = signupMapper;
    }

    // ------------------------------------------------------------------ 查询

    /** 后台活动列表（不含场次明细，点进详情再拉）。 */
    public PageResult<ActivityDtos.ActivityVO> pageForAdmin(String keyword, Integer status, PageQuery pageQuery) {
        LambdaQueryWrapper<Activity> wrapper = Wrappers.<Activity>lambdaQuery()
                .and(hasText(keyword), w -> w.like(Activity::getTitle, keyword)
                        .or().like(Activity::getDescription, keyword))
                .eq(status != null, Activity::getStatus, status)
                .orderByDesc(Activity::getId);
        return selectPage(wrapper, pageQuery, false);
    }

    /** 小程序活动列表：只看上线的，且带场次（要展示剩余名额）。 */
    public PageResult<ActivityDtos.ActivityVO> pagePublished(PageQuery pageQuery) {
        LambdaQueryWrapper<Activity> wrapper = Wrappers.<Activity>lambdaQuery()
                .eq(Activity::getStatus, 1)
                .orderByDesc(Activity::getId);
        return selectPage(wrapper, pageQuery, true);
    }

    /** 活动详情（带场次）。 */
    public ActivityDtos.ActivityVO detail(Long id) {
        Activity activity = requireActivity(id);
        List<ActivitySession> sessions = sessionMapper.selectList(Wrappers.<ActivitySession>lambdaQuery()
                .eq(ActivitySession::getActivityId, id)
                .orderByAsc(ActivitySession::getStartTime));
        return toVO(activity, sessions);
    }

    // ------------------------------------------------------------------ 写操作

    @Transactional
    public ActivityDtos.ActivityVO create(ActivityDtos.ActivitySaveRequest request) {
        validateWindow(request.signupStartTime(), request.signupEndTime());
        Activity activity = new Activity();
        applyRequest(activity, request);
        activityMapper.insert(activity);
        return detail(activity.getId());
    }

    @Transactional
    public ActivityDtos.ActivityVO update(Long id, ActivityDtos.ActivitySaveRequest request) {
        requireActivity(id);
        validateWindow(request.signupStartTime(), request.signupEndTime());
        Activity patch = new Activity();
        patch.setId(id);
        applyRequest(patch, request);
        activityMapper.updateById(patch);
        return detail(id);
    }

    @Transactional
    public void delete(Long id) {
        requireActivity(id);
        Long signed = signupMapper.selectCount(Wrappers.<ActivitySignup>lambdaQuery()
                .eq(ActivitySignup::getActivityId, id));
        if (signed != null && signed > 0) {
            throw BizException.of(ResultCode.CONFLICT, "该活动已有报名记录，不能删除，请改为下线");
        }
        sessionMapper.delete(Wrappers.<ActivitySession>lambdaQuery()
                .eq(ActivitySession::getActivityId, id));
        activityMapper.deleteById(id);
    }

    // ------------------------------------------------------------------ 场次

    @Transactional
    public ActivityDtos.SessionVO createSession(Long activityId, ActivityDtos.SessionSaveRequest request) {
        requireActivity(activityId);
        validateSessionWindow(request.startTime(), request.endTime());
        ActivitySession session = new ActivitySession();
        session.setActivityId(activityId);
        session.setName(request.name());
        session.setStartTime(request.startTime());
        session.setEndTime(request.endTime());
        session.setCapacity(request.capacity());
        session.setRemain(request.capacity());
        session.setPrice(request.price());
        session.setVersion(0);
        sessionMapper.insert(session);
        return toSessionVO(session);
    }

    /**
     * 修改场次。
     *
     * <p>容量变化的处理：先算出"已报名人数 = capacity - remain"，新容量不能小于已报名人数；
     * 然后把 remain 调整为 {@code 新容量 - 已报名}。整个 update 带 version 条件，
     * 并发修改时后提交的事务会拿到 0 行更新，这里显式抛冲突提示而不是静默覆盖。</p>
     */
    @Transactional
    public ActivityDtos.SessionVO updateSession(Long sessionId, ActivityDtos.SessionSaveRequest request) {
        ActivitySession session = requireSession(sessionId);
        validateSessionWindow(request.startTime(), request.endTime());
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        int remain = session.getRemain() == null ? 0 : session.getRemain();
        int signed = Math.max(capacity - remain, 0);
        if (request.capacity() < signed) {
            throw BizException.param("新容量不能小于已报名人数 " + signed);
        }

        ActivitySession patch = new ActivitySession();
        patch.setId(sessionId);
        patch.setName(request.name());
        patch.setStartTime(request.startTime());
        patch.setEndTime(request.endTime());
        patch.setCapacity(request.capacity());
        patch.setRemain(request.capacity() - signed);
        patch.setPrice(request.price());
        patch.setVersion(session.getVersion());
        int affected = sessionMapper.updateById(patch);
        if (affected == 0) {
            throw BizException.of(ResultCode.CONFLICT, "场次已被他人修改，请刷新后重试");
        }
        return toSessionVO(sessionMapper.selectById(sessionId));
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        requireSession(sessionId);
        Long signed = signupMapper.countActiveBySession(sessionId);
        if (signed > 0) {
            throw BizException.of(ResultCode.CONFLICT, "该场次已有有效报名，不能删除");
        }
        sessionMapper.deleteById(sessionId);
    }

    // ------------------------------------------------------------------ 内部方法

    private PageResult<ActivityDtos.ActivityVO> selectPage(LambdaQueryWrapper<Activity> wrapper,
                                                           PageQuery pageQuery, boolean withSessions) {
        Page<Activity> page = activityMapper.selectPage(pageQuery.toPage(), wrapper);
        List<Activity> records = page.getRecords();
        if (records.isEmpty()) {
            return PageResult.of(List.of(), page.getCurrent(), page.getSize(), page.getTotal());
        }
        Map<Long, List<ActivitySession>> sessionsByActivity = withSessions
                ? loadSessions(records.stream().map(Activity::getId).toList())
                : Map.of();
        List<ActivityDtos.ActivityVO> items = records.stream()
                .map(activity -> toVO(activity, sessionsByActivity.getOrDefault(activity.getId(), List.of())))
                .toList();
        return PageResult.of(items, page.getCurrent(), page.getSize(), page.getTotal());
    }

    /** 一次 IN 查询取回所有场次，再按 activityId 分组，避免 N+1。 */
    private Map<Long, List<ActivitySession>> loadSessions(List<Long> activityIds) {
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        List<ActivitySession> sessions = sessionMapper.selectList(Wrappers.<ActivitySession>lambdaQuery()
                .in(ActivitySession::getActivityId, activityIds)
                .orderByAsc(ActivitySession::getStartTime));
        Map<Long, List<ActivitySession>> grouped = new HashMap<>();
        for (ActivitySession session : sessions) {
            grouped.computeIfAbsent(session.getActivityId(), key -> new ArrayList<>()).add(session);
        }
        return grouped;
    }

    private void applyRequest(Activity activity, ActivityDtos.ActivitySaveRequest request) {
        activity.setTitle(request.title());
        activity.setCoverUrl(request.coverUrl());
        activity.setRegionId(request.regionId());
        activity.setDescription(request.description());
        activity.setStatus(request.status() == null ? 0 : request.status());
        activity.setSignupStartTime(request.signupStartTime());
        activity.setSignupEndTime(request.signupEndTime());
        activity.setStartTime(request.startTime());
        activity.setEndTime(request.endTime());
    }

    private void validateWindow(LocalDateTime signupStart, LocalDateTime signupEnd) {
        if (signupStart != null && signupEnd != null && signupEnd.isBefore(signupStart)) {
            throw BizException.param("报名结束时间不能早于开始时间");
        }
    }

    private void validateSessionWindow(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw BizException.param("场次结束时间不能早于开始时间");
        }
    }

    private Activity requireActivity(Long id) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            throw BizException.notFound("活动");
        }
        return activity;
    }

    private ActivitySession requireSession(Long id) {
        ActivitySession session = sessionMapper.selectById(id);
        if (session == null) {
            throw BizException.notFound("场次");
        }
        return session;
    }

    private ActivityDtos.ActivityVO toVO(Activity activity, List<ActivitySession> sessions) {
        LocalDateTime now = LocalDateTime.now();
        boolean open = activity.getStatus() != null && activity.getStatus() == 1
                && (activity.getSignupStartTime() == null || !now.isBefore(activity.getSignupStartTime()))
                && (activity.getSignupEndTime() == null || !now.isAfter(activity.getSignupEndTime()));
        List<ActivityDtos.SessionVO> sessionVOs = sessions == null ? List.of()
                : sessions.stream().map(this::toSessionVO).toList();
        return new ActivityDtos.ActivityVO(activity.getId(), activity.getTitle(), activity.getCoverUrl(),
                activity.getRegionId(), activity.getDescription(), activity.getStatus(),
                activity.getSignupStartTime(), activity.getSignupEndTime(),
                activity.getStartTime(), activity.getEndTime(), open, sessionVOs);
    }

    private ActivityDtos.SessionVO toSessionVO(ActivitySession session) {
        int remain = session.getRemain() == null ? 0 : session.getRemain();
        return new ActivityDtos.SessionVO(session.getId(), session.getActivityId(), session.getName(),
                session.getStartTime(), session.getEndTime(), session.getCapacity(), remain,
                session.getPrice(), remain <= 0);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** 供统计服务复用：活动标题映射。 */
    Map<Long, String> activityTitles(List<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return map;
        }
        for (Activity activity : activityMapper.selectBatchIds(distinct)) {
            map.put(activity.getId(), activity.getTitle());
        }
        return map;
    }
}
