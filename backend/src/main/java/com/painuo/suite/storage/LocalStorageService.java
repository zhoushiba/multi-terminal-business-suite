package com.painuo.suite.storage;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.config.AppProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘存储实现（默认）。
 *
 * <p>落盘路径 {@code {localRoot}/{yyyy}/{MM}/{uuid}.{ext}}，通过
 * {@code /uploads/**} 资源映射对外提供访问，避免把二进制塞进数据库。</p>
 */
public class LocalStorageService implements StorageService {

    private static final Set<String> ALLOWED_EXT =
            Set.of("jpg", "jpeg", "png", "webp", "gif", "svg", "pdf", "mp4");
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    private final AppProperties.Storage props;

    public LocalStorageService(AppProperties.Storage props) {
        this.props = props;
    }

    @Override
    public String store(InputStream in, String originalName, long size) {
        if (size <= 0 || size > MAX_SIZE) {
            throw BizException.of(ResultCode.PARAM_INVALID, "文件大小需在 1B ~ 10MB 之间");
        }
        String ext = extensionOf(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw BizException.of(ResultCode.PARAM_INVALID, "不支持的文件类型：" + ext);
        }
        String relative = LocalDate.now().format(MONTH) + "/" + UUID.randomUUID().toString().replace("-", "")
                + "." + ext;
        Path target = Paths.get(props.localRoot()).resolve(relative).normalize();
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw BizException.of(ResultCode.STORAGE_ERROR, "写入文件失败：" + ex.getMessage());
        }
        String base = props.publicBaseUrl() == null || props.publicBaseUrl().isBlank()
                ? "/uploads" : props.publicBaseUrl();
        return base.endsWith("/") ? base + relative : base + "/" + relative;
    }

    @Override
    public void delete(String url) {
        if (url == null || !url.contains("/uploads/")) {
            return;
        }
        String relative = url.substring(url.indexOf("/uploads/") + "/uploads/".length());
        try {
            Files.deleteIfExists(Paths.get(props.localRoot()).resolve(relative).normalize());
        } catch (IOException ignored) {
            // 删除失败不影响主流程，留待运维清理
        }
    }

    @Override
    public String backend() {
        return "local(" + props.localRoot() + ")";
    }

    @Override
    public long maxSize() {
        return MAX_SIZE;
    }

    private String extensionOf(String name) {
        if (name == null || !name.contains(".")) {
            throw BizException.of(ResultCode.PARAM_INVALID, "文件名缺少扩展名");
        }
        return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}
