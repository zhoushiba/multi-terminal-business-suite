package com.painuo.suite.service;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件上传门面：只管校验（空文件/超大），具体落盘或上云交给
 * {@link StorageService} 的实现，切换 OSS 时这里一行都不用改。
 */
@Service
public class UploadService {

    private final StorageService storageService;

    public UploadService(StorageService storageService) {
        this.storageService = storageService;
    }

    /** 上传结果：前端拿 url 直接回填表单封面字段。 */
    public record UploadResult(String url, String backend, long size) {
    }

    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw BizException.param("请选择要上传的文件");
        }
        if (file.getSize() > storageService.maxSize()) {
            throw BizException.of(ResultCode.STORAGE_ERROR,
                    "文件超过上限 " + storageService.maxSize() / 1024 / 1024 + "MB");
        }
        try (InputStream in = file.getInputStream()) {
            String url = storageService.store(in, file.getOriginalFilename(), file.getSize());
            return new UploadResult(url, storageService.backend(), file.getSize());
        } catch (IOException ex) {
            throw BizException.of(ResultCode.STORAGE_ERROR, "文件读取失败: " + ex.getMessage());
        }
    }
}
