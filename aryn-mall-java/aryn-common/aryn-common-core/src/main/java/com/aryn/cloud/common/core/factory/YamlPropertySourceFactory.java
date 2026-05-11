
package com.aryn.cloud.common.core.factory;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * 读取自定义 YAML 文件
 * @author 雨滴kian
 * @date 2024/11/25
 */

public class YamlPropertySourceFactory implements PropertySourceFactory {

	@Override
	public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
		Properties properties = loadYamlIntoProperties(resource.getResource());
		String sourceName = Objects.nonNull(name) ? name : resource.getResource().getFilename();
		return new PropertiesPropertySource(sourceName, properties);
	}

	private Properties loadYamlIntoProperties(Resource resource) throws IOException {
		if (!resource.exists()) {
			throw new IllegalArgumentException("Resource " + resource + " does not exist");
		}

		YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
		factory.setResources(resource);
		factory.afterPropertiesSet();
		return factory.getObject();
	}

}
