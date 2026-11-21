package com.painuo.suite.controller;

import com.painuo.suite.common.Result;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传（封面图等）。
 *
 * <p>落地在本地 {@code app.storage.local-root} 目录并通过 {@code /uploads/**} 暴露；
 * 把 {@code app.storage.type} 改成 {@code oss} 即可切到对象存储，Controller 无需改动。</p>
 */
@RestController
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/api/upload")
    @RequirePerm("upload:write")
    public Result<UploadService.UploadResult> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(uploadService.upload(file), "上传成功");
    }
}
