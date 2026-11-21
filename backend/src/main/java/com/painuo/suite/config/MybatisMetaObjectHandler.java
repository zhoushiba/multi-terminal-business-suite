package com.painuo.suite.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 *
 * <p>实体里用 {@code @TableField(fill = FieldFill.INSERT)} / {@code INSERT_UPDATE} 标注后，
 * 由这里统一写入，业务代码不再手写 createdAt / updatedAt。
 * {@code strictXxxFill} 只在实体真的声明了该字段时才写，避免给没有 updatedAt 的表硬塞值。</p>
 */
@Configuration
public class MybatisMetaObjectHandler {

    @Bean
    public MetaObjectHandler auditMetaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
