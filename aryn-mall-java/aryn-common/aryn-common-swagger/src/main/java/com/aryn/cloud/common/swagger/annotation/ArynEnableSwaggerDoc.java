
package com.aryn.cloud.common.swagger.annotation;

import com.aryn.cloud.common.swagger.config.SwaggerAutoConfiguration;
import com.aryn.cloud.common.swagger.config.SwaggerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

/**
 * 开启 spring doc
 *
 * @author 雨滴kian
 * @date 2024/05/06 15:49
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@EnableConfigurationProperties(SwaggerProperties.class)
@Import({ SwaggerAutoConfiguration.class })
public @interface ArynEnableSwaggerDoc {

}
