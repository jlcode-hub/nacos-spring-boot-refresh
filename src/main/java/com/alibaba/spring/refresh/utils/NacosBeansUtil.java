package com.alibaba.spring.refresh.utils;

import com.alibaba.nacos.spring.context.annotation.config.NacosValueAnnotationBeanPostProcessor;
import com.alibaba.nacos.spring.util.NacosBeanUtils;
import com.alibaba.spring.refresh.binder.NacosRefreshScopeBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;

/**
 * @author: meng.liu
 * @date: 2020/8/5
 * TODO:
 */
public class NacosBeansUtil {

    public static void registerNacosRefreshScopeBeanPostProcessor(
            BeanDefinitionRegistry registry) {
        NacosBeanUtils.registerInfrastructureBeanIfAbsent(registry,
                NacosRefreshScopeBeanPostProcessor.BEAN_NAME,
                NacosRefreshScopeBeanPostProcessor.class);
    }

}
