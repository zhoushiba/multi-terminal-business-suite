package com.painuo.suite;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 数字内容与数据服务业务中台 —— 服务端启动类。
 *
 * <p>三端共用同一套 RESTful 接口：运营后台（Vue3）与微信小程序（uni-app）。</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.painuo.suite.mapper")
public class SuiteApplication {

    public static void main(String[] args) {
        SpringApplication.run(SuiteApplication.class, args);
    }
}
