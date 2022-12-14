package club.bigtian.notice.service.impl;

import club.bigtian.notice.config.ExceptionNoticeConfig;
import club.bigtian.notice.service.ISystemCacheService;
import club.bigtian.notice.utils.RequestUtils;
import net.jodah.expiringmap.ExpirationPolicy;
import net.jodah.expiringmap.ExpiringMap;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.TimeUnit;

/**
 * @author bigtian
 * @Description: 系统缓存
 * @date 2022/9/1211:55
 */
public class ISystemCacheServiceImpl implements ISystemCacheService {
    @Autowired
    private ExceptionNoticeConfig config;

    private static final ExpiringMap<String, String> SYSTEM_CACHE_MAP = ExpiringMap.builder()
            .variableExpiration()
            .expirationPolicy(ExpirationPolicy.CREATED)
            .build();


    /**
     * 过期时间
     *
     * @return boolean
     */
    @Override
    public boolean expirationTime() {
        return SYSTEM_CACHE_MAP.containsKey(RequestUtils.getRequestPath());
    }


    /**
     * 把请求路径缓存起来
     *
     * @return {@link String}
     */
    @Override
    public String putVal() {
        return SYSTEM_CACHE_MAP.put(RequestUtils.getRequestPath(), "", config.getTimeout(), TimeUnit.SECONDS);
    }
}
