package com.alibaba.spring.refresh.binder;

import com.alibaba.spring.refresh.annotation.NacosRefreshScope;
import com.alibaba.spring.refresh.event.NacosRefreshScopePropertiesBeanBoundEvent;
import com.alibaba.spring.refresh.utils.ConfigTypeUtils;
import com.alibaba.boot.nacos.config.properties.NacosConfigProperties;
import com.alibaba.nacos.api.annotation.NacosProperties;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.AbstractListener;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.spring.beans.factory.annotation.ConfigServiceBeanBuilder;
import com.alibaba.nacos.spring.context.event.config.EventPublishingConfigService;
import com.alibaba.nacos.spring.context.event.config.NacosConfigEvent;
import com.alibaba.nacos.spring.context.event.config.NacosConfigMetadataEvent;
import com.alibaba.nacos.spring.util.NacosUtils;
import com.alibaba.nacos.spring.util.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.PropertyValues;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;
import org.springframework.validation.DataBinder;

import java.util.Map;
import java.util.Properties;

import static com.alibaba.nacos.spring.util.NacosBeanUtils.getConfigServiceBeanBuilder;
import static com.alibaba.nacos.spring.util.NacosUtils.getContent;
import static org.springframework.core.annotation.AnnotationUtils.findAnnotation;
import static org.springframework.core.annotation.AnnotationUtils.getAnnotationAttributes;
import static org.springframework.util.StringUtils.hasText;

/**
 * @author: meng.liu
 * @date: 2020/8/5
 * TODO:
 */
public class NacosRefreshScopePropertiesBinder {

	public static final String BEAN_NAME = "nacosRefreshScopePropertiesBinder";

	private static final Logger logger = LoggerFactory
			.getLogger(NacosRefreshScopePropertiesBinder.class);

	private final ConfigurableApplicationContext applicationContext;

	private final Environment environment;

	private final ApplicationEventPublisher applicationEventPublisher;

	private final ConfigServiceBeanBuilder configServiceBeanBuilder;

	private NacosConfigProperties nacosConfigProperties;

	protected NacosRefreshScopePropertiesBinder(
			ConfigurableApplicationContext applicationContext) {
		Assert.notNull(applicationContext,
				"ConfigurableApplicationContext must not be null!");
		this.applicationContext = applicationContext;
		this.environment = applicationContext.getEnvironment();
		this.applicationEventPublisher = applicationContext;
		this.configServiceBeanBuilder = getConfigServiceBeanBuilder(applicationContext);
	}

	protected void bind(Object bean, String beanName) {

		NacosRefreshScope scope = findAnnotation(bean.getClass(),
				NacosRefreshScope.class);

		ConfigurationProperties properties = findAnnotation(bean.getClass(),
				ConfigurationProperties.class);

		bind(bean, beanName, scope, properties);

	}

	protected void bind(final Object bean, final String beanName,
			final NacosRefreshScope nacosRefreshScope, ConfigurationProperties configurationProperties) {

		Assert.notNull(bean, "Bean must not be null!");

		Assert.notNull(nacosRefreshScope, "NacosRefreshScope must not be null!");

		Assert.notNull(configurationProperties, "ConfigurationProperties must not be null!");

		final ConfigService configService = configServiceBeanBuilder
				.build(nacosRefreshScope.properties());
		String dataId = this.dataId(nacosRefreshScope);
		String groupId = this.groupId(nacosRefreshScope);
		String fileType = NacosUtils.readTypeFromDataId(dataId);
		String type = ConfigTypeUtils.isConfigType(fileType) ? fileType : nacosRefreshScope.type().getType();
		// Add a Listener if auto-refreshed
		if (nacosRefreshScope.value()) {
			Listener listener = new AbstractListener() {
				@Override
				public void receiveConfigInfo(String config) {
					doBind(bean, beanName, dataId, groupId, type, nacosRefreshScope, configurationProperties, config,
							configService);
				}
			};

			try {//
				if (configService instanceof EventPublishingConfigService) {
					((EventPublishingConfigService) configService).addListener(dataId,
							groupId, type, listener);
				}
				else {
					configService.addListener(dataId, groupId, listener);
				}
			}
			catch (NacosException e) {
				if (logger.isErrorEnabled()) {
					logger.error(e.getMessage(), e);
				}
			}
		}

		String content = getContent(configService, dataId, groupId);

		if (hasText(content)) {
			doBind(bean, beanName, dataId, groupId, type, nacosRefreshScope, configurationProperties, content,
					configService);
		}
	}

	protected void doBind(Object bean, String beanName, String dataId, String groupId,
			String type, NacosRefreshScope nacosRefreshScope, ConfigurationProperties configurationProperties, String content,
			ConfigService configService) {
		PropertyValues propertyValues = NacosUtils.resolvePropertyValues(bean, configurationProperties.prefix(),
				dataId, groupId, content, type);
		doBind(bean, nacosRefreshScope, configurationProperties, propertyValues);
		publishBoundEvent(bean, beanName, dataId, groupId, nacosRefreshScope, content,
				configService);
		publishMetadataEvent(bean, beanName, dataId, groupId, nacosRefreshScope);
	}

	protected void publishBoundEvent(Object bean, String beanName, String dataId,
									 String groupId, NacosRefreshScope nacosRefreshScope, String content,
									 ConfigService configService) {
		NacosConfigEvent event = new NacosRefreshScopePropertiesBeanBoundEvent(
				configService, dataId, groupId, bean, beanName, nacosRefreshScope, content);
		applicationEventPublisher.publishEvent(event);
	}

	protected void publishMetadataEvent(Object bean, String beanName, String dataId,
										String groupId, NacosRefreshScope nacosRefreshScope) {

		NacosProperties nacosProperties = nacosRefreshScope.properties();

		NacosConfigMetadataEvent metadataEvent = new NacosConfigMetadataEvent(nacosRefreshScope);

		// Nacos Metadata
		metadataEvent.setDataId(dataId);
		metadataEvent.setGroupId(groupId);
		Properties resolvedNacosProperties = configServiceBeanBuilder
				.resolveProperties(nacosProperties);
		Map<String, Object> nacosPropertiesAttributes = getAnnotationAttributes(
				nacosProperties);
		metadataEvent.setNacosPropertiesAttributes(nacosPropertiesAttributes);
		metadataEvent.setNacosProperties(resolvedNacosProperties);

		// Bean Metadata
		Class<?> beanClass = bean.getClass();
		metadataEvent.setBeanName(beanName);
		metadataEvent.setBean(bean);
		metadataEvent.setBeanType(beanClass);
		metadataEvent.setAnnotatedElement(beanClass);

		// Publish event
		applicationEventPublisher.publishEvent(metadataEvent);
	}

	private void doBind(Object bean, NacosRefreshScope nacosRefreshScope, ConfigurationProperties configurationProperties,
			PropertyValues propertyValues) {
		ObjectUtils.cleanMapOrCollectionField(bean);
		DataBinder dataBinder = new DataBinder(bean);
		dataBinder.setAutoGrowNestedPaths(nacosRefreshScope.ignoreNestedProperties());
		dataBinder.setIgnoreInvalidFields(configurationProperties.ignoreInvalidFields());
		dataBinder.setIgnoreUnknownFields(configurationProperties.ignoreUnknownFields());
		dataBinder.bind(propertyValues);
	}


	private String groupId(NacosRefreshScope nacosRefreshScope){
		if( StringUtils.isBlank(nacosRefreshScope.groupId()) ){
			return this.getConfigProperties().getGroup();
		}else{
			return NacosUtils.readFromEnvironment(nacosRefreshScope.groupId(),
					environment);
		}
	}

	private String dataId(NacosRefreshScope nacosRefreshScope){
		if( StringUtils.isBlank(nacosRefreshScope.dataId()) ){
			return this.getConfigProperties().getDataId();
		}else{
			return NacosUtils.readFromEnvironment(nacosRefreshScope.groupId(),
					environment);
		}
	}

	private NacosConfigProperties getConfigProperties(){
		if( null != nacosConfigProperties ){
			return nacosConfigProperties;
		}
		nacosConfigProperties = applicationContext.getBean(NacosConfigProperties.class);
		Assert.notNull(nacosConfigProperties, "Nacos config properties cannot be null.");
		return nacosConfigProperties;
	}
}
