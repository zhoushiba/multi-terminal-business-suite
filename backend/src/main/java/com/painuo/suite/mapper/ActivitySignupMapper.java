package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.ActivitySignup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ActivitySignupMapper extends BaseMapper<ActivitySignup> {

    /** 某场次的有效报名数（1 已报名 / 2 已核销），用于对账"名额账实是否相符"。 */
    @Select("""
            SELECT COUNT(*) FROM activity_signup
             WHERE session_id = #{sessionId} AND status IN (1, 2)
            """)
    long countActiveBySession(@Param("sessionId") Long sessionId);

    /** 按核销码查（现场扫码核销入口）。 */
    @Select("""
            SELECT * FROM activity_signup WHERE code = #{code} LIMIT 1
            """)
    ActivitySignup selectByCode(@Param("code") String code);
}
