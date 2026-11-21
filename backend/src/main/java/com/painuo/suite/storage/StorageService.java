package com.painuo.suite.storage;

import java.io.InputStream;

/**
 * 对象存储适配层。
 *
 * <p>运营后台和小程序都要传图，但本地开发不该被迫先申请 OSS。
 * 于是抽象成「本地磁盘实现 + OSS/S3 兼容实现」，用 {@code app.storage.type} 切换，
 * 业务代码只依赖 {@code store(InputStream, filename) -> url}。</p>
 */
public interface StorageService {

    /**
     * 上传文件。
     *
     * @param in           文件流（由调用方负责关闭）
     * @param originalName 原始文件名，用于取扩展名
     * @param size         字节数，用于大小校验
     * @return 可公网/内网访问的 URL
     */
    String store(InputStream in, String originalName, long size);

    /** 删除（本地实现直接删文件，OSS 实现调用 deleteObject）。 */
    void delete(String url);

    /** 当前实现标识，便于排查"图挂了是本地还是 OSS"。 */
    String backend();

    /** 单文件最大字节数。 */
    long maxSize();
}
