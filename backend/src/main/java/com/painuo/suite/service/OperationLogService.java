package com.painuo.suite.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.painuo.suite.common.PageQuery;
import com.painuo.suite.common.PageResult;
import com.painuo.suite.entity.OperationLog;
import com.painuo.suite.mapper.OperationLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 操作日志（审计）查询。
 *
 * <p>写入侧计划由 {@code OperationLogAspect} 统一织入（见 docs/roadmap.md 的 P1），
 * 当前阶段先把「系统配置 / 操作日志」页面需要的只读能力补齐：
 * 按关键字（操作人 / 动作 / 路径 / traceId）过滤 + 时间倒序分页。</p>
 */
@Service
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public OperationLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 分页查询操作日志。
     *
     * @param keyword   可选，模糊匹配操作人、动作、路径、traceId
     * @param pageQuery 分页参数（size 上限 100，见 {@link PageQuery}）
     */
    public PageResult<OperationLog> page(String keyword, PageQuery pageQuery) {
        LambdaQueryWrapper<OperationLog> wrapper = Wrappers.<OperationLog>lambdaQuery()
                .and(StringUtils.hasText(keyword), w -> w.like(OperationLog::getUsername, keyword)
                        .or().like(OperationLog::getAction, keyword)
                        .or().like(OperationLog::getPath, keyword)
                        .or().like(OperationLog::getTraceId, keyword))
                .orderByDesc(OperationLog::getCreatedAt)
                .orderByDesc(OperationLog::getId);
        Page<OperationLog> page = operationLogMapper.selectPage(pageQuery.toPage(), wrapper);
        return pageQuery.wrap(page);
    }
}
