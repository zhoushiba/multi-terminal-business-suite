package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** 用户的有效权限标识（去重）。4 表 join 写成一条 SQL，避免 N+1。 */
    @Select("""
            SELECT DISTINCT m.perm
            FROM sys_user_role ur
            JOIN sys_role_menu rm ON rm.role_id = ur.role_id
            JOIN sys_menu m ON m.id = rm.menu_id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
              AND m.perm IS NOT NULL AND m.perm <> ''
            """)
    List<String> selectPermissions(Long userId);

    /** 用户的角色编码。 */
    @Select("""
            SELECT DISTINCT r.code
            FROM sys_user_role ur
            JOIN sys_role r ON r.id = ur.role_id
            WHERE ur.user_id = #{userId}
            """)
    List<String> selectRoleCodes(Long userId);
}
