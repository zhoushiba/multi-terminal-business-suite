package com.painuo.suite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.painuo.suite.entity.SysMenu;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /**
     * 用户可见菜单（目录 + 菜单，不含按钮），动态路由的数据源。
     *
     * <p>admin 角色看全量，其它角色按 role_menu 授权。
     * 用 {@code <script>} 动态 SQL 而不是两条方法，是为了让"谁能看到什么"只在一个地方定义。</p>
     */
    @Select("""
            <script>
            SELECT DISTINCT m.* FROM sys_menu m
            <if test="!isAdmin">
              JOIN sys_role_menu rm ON rm.menu_id = m.id
              JOIN sys_user_role ur ON ur.role_id = rm.role_id
            </if>
            WHERE m.type IN (0, 1)
            <if test="!isAdmin">
              AND ur.user_id = #{userId}
            </if>
            ORDER BY m.sort ASC, m.id ASC
            </script>
            """)
    List<SysMenu> selectRoutableMenus(@Param("userId") Long userId, @Param("isAdmin") boolean isAdmin);
}
