package com.light.reactagent.tools.image;

/**
 * 图片 URL 处理抽象层
 * <p>
 * 将"图片 URL 如何暴露给前端"从业务代码中解耦，便于后续切换为后端代理地址
 * （如接入 CDN、加签、防盗链、referrer 限制等）。
 * 当前默认实现为 {@link DirectImageProxyService}（直接透传 Pexels 原 URL）。
 * 接入代理时只需新增一个实现类并切换 Spring Bean 即可。
 */
public interface ImageProxyService {

    /**
     * 处理原图 URL
     *
     * @param originalUrl Pexels 返回的原始图片 URL
     * @return 最终暴露给前端的 URL
     */
    String processUrl(String originalUrl);

    /**
     * 处理缩略图 URL
     *
     * @param originalUrl Pexels 返回的原始缩略图 URL
     * @return 最终暴露给前端的 URL
     */
    String processThumbnailUrl(String originalUrl);
}
