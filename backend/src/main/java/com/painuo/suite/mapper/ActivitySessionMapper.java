package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.ActivitySession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActivitySessionMapper extends BaseMapper<ActivitySession> {

    /**
     * 名额原子扣减（本项目最重要的 5 行 SQL）。
     *
     * <p>{@code WHERE remain > 0} 把「判断」和「扣减」压进同一条语句，
     * 由 InnoDB 行锁串行化，彻底消除"先查再改"的 TOCTOU 竞态。
     * 返回 0 表示名额已耗尽，调用方据此抛 SIGNUP_SOLD_OUT。</p>
     *
     * @return 影响行数：1 成功；0 名额不足
     */
    @Update("""
            UPDATE activity_session
               SET remain = remain - 1
             WHERE id = #{sessionId}
               AND remain > 0
            """)
    int deductRemain(@Param("sessionId") Long sessionId);

    /** 取消报名时归还名额（同样带 remain &lt; capacity 保护，防止超还）。 */
    @Update("""
            UPDATE activity_session
               SET remain = remain + 1
             WHERE id = #{sessionId}
               AND remain < capacity
            """)
    int restoreRemain(@Param("sessionId") Long sessionId);
}
