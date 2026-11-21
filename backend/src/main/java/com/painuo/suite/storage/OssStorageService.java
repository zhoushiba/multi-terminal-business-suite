package com.painuo.suite.storage;

import com.painuo.suite.common.BizException;
import com.painuo.suite.common.ResultCode;
import com.painuo.suite.config.AppProperties;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

/**
 * 阿里云 OSS 实现（S3/OSS 兼容协议，OSS2 签名）。
 *
 * <p>说明（诚实标注）：本机没有 OSS 账号，因此这条路径<b>未在本地执行验证</b>，
 * 只有 {@link LocalStorageService} 跑通。代码按 OSS2 签名规范实现：
 * </p>
 *
 * <pre>
 * Signature = base64( HMAC-SHA1( AccessKeySecret,
 *     VERB + "\n" + Content-MD5 + "\n" + Content-Type + "\n" + Date + "\n"
 *          + CanonicalizedOSSHeaders + CanonicalizedResource ) )
 * Authorization: OSS {AccessKeyId}:{Signature}
 * </pre>
 *
 * <p>上线时把 {@code app.storage.type} 改成 {@code oss} 并填好
 * endpoint / bucket / AK 即可，无需改动业务代码。</p>
 */
public class OssStorageService implements StorageService {

    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final DateTimeFormatter GMT =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);

    private final AppProperties.Storage props;
    private final HttpClient httpClient;

    public OssStorageService(AppProperties.Storage props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String store(InputStream in, String originalName, long size) {
        if (size <= 0 || size > MAX_SIZE) {
            throw BizException.of(ResultCode.PARAM_INVALID, "文件大小需在 1B ~ 20MB 之间");
        }
        String ext = originalName != null && originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)
                : "bin";
        String key = "cms/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String contentType = guessContentType(ext);
        String date = ZonedDateTime.now(ZoneOffset.UTC).format(GMT);

        try {
            byte[] payload = in.readAllBytes();
            String resource = "/" + props.bucket() + "/" + key;
            String signature = sign("PUT", contentType, date, resource);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimSlash(props.endpoint()) + resource))
                    .timeout(Duration.ofSeconds(30))
                    .header("Date", date)
                    .header("Content-Type", contentType)
                    .header("Authorization", "OSS " + props.accessKeyId() + ":" + signature)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() / 100 != 2) {
                throw BizException.of(ResultCode.STORAGE_ERROR, "OSS 上传失败，状态码 " + response.statusCode());
            }
        } catch (IOException ex) {
            throw BizException.of(ResultCode.STORAGE_ERROR, "OSS 上传异常：" + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.STORAGE_ERROR, "OSS 上传被中断");
        }
        return trimSlash(props.publicBaseUrl() == null ? trimSlash(props.endpoint()) + "/" + props.bucket()
                : props.publicBaseUrl()) + "/" + key;
    }

    @Override
    public void delete(String url) {
        // 生产实现：签名 DELETE 请求；本地无 OSS，保留骨架并明确不做静默成功
        throw BizException.of(ResultCode.STORAGE_ERROR, "OSS 删除未实现，请通过控制台或补齐 SDK 后启用");
    }

    @Override
    public String backend() {
        return "oss(" + props.bucket() + ")";
    }

    @Override
    public long maxSize() {
        return MAX_SIZE;
    }

    private String sign(String verb, String contentType, String date, String resource) {
        String toSign = verb + "\n\n" + contentType + "\n" + date + "\n" + resource;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(props.accessKeySecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            return Base64.getEncoder().encodeToString(mac.doFinal(toSign.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw BizException.of(ResultCode.STORAGE_ERROR, "OSS 签名失败：" + ex.getMessage());
        }
    }

    /** 预留：如需校验下载完整性，可对比 Content-MD5。 */
    @SuppressWarnings("unused")
    private String md5Base64(byte[] bytes) throws Exception {
        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("MD5").digest(bytes));
    }

    private String guessContentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "mp4" -> "video/mp4";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
