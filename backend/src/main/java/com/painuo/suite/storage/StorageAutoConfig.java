package com.painuo.suite.storage;

import com.painuo.suite.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 存储实现选择器：{@code app.storage.type=local|oss}。 */
@Configuration
public class StorageAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageAutoConfig.class);

    @Bean
    public StorageService storageService(AppProperties appProperties) {
        AppProperties.Storage storage = appProperties.storage();
        StorageService service;
        if ("oss".equalsIgnoreCase(storage.type())) {
            service = new OssStorageService(storage);
        } else {
            service = new LocalStorageService(storage);
        }
        log.info("文件存储实现: {}", service.backend());
        return service;
    }
}
