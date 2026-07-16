package com.light.reactagent.tools.image;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 直接透传实现 —— 当前阶段不做任何 URL 转换，原样返回 Pexels URL。
 * 后续如需后端代理（防盗链 / CDN / 加签），新增实现类并替换此 Bean 即可。
 */
@Service
@Slf4j
public class DirectImageProxyService implements ImageProxyService {

    @Override
    public String processUrl(String originalUrl) {
        return originalUrl;
    }

    @Override
    public String processThumbnailUrl(String originalUrl) {
        return originalUrl;
    }
}
