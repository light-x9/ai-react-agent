package com.light.reactagent.config;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring Bean 获取工具类
 * <p>
 * 让非 Spring Bean 的对象（如每次请求 new 出来的 Agent）能通过静态方法获取 Spring Bean。
 * <p>
 * 使用方式：SpringBeanUtils.getBean(FileMetadataManager.class)
 */
@Component
public class SpringBeanUtils implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        SpringBeanUtils.applicationContext = context;
    }

    /**
     * 根据类型获取 Spring Bean
     *
     * @param clazz Bean 类型
     * @param <T>   Bean 泛型
     * @return Bean 实例，未找到时返回 null
     */
    public static <T> T getBean(Class<T> clazz) {
        if (applicationContext == null) {
            return null;
        }
        try {
            return applicationContext.getBean(clazz);
        } catch (BeansException e) {
            return null;
        }
    }
}
