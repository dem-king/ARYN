
package com.aryn.cloud.auth.config;

import com.anji.captcha.properties.AjCaptchaProperties;
import com.anji.captcha.service.CaptchaCacheService;
import com.anji.captcha.service.CaptchaService;
import com.anji.captcha.service.impl.CaptchaServiceFactory;
import com.anji.captcha.util.ImageUtils;
import com.aryn.cloud.auth.service.CaptchaCacheRedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Configuration
public class CaptchaConfig {

	private static final String BASE_IMAGE_DIR = "captcha/jigsaw/original/";

	private static final String BASE_BLOCK_DIR = "captcha/jigsaw/slidingBlock/";

	private static final int MAX_BASE_IMAGES = 99;

	@Autowired
	StringRedisTemplate redisTemplate;

	@Bean(name = "AjCaptchaCacheService")
	@Primary
	public CaptchaCacheService captchaCacheService(AjCaptchaProperties config) {
		// 缓存类型redis/local/....
		CaptchaCacheService ret = CaptchaServiceFactory.getCache(config.getCacheType().name());
		if (ret instanceof CaptchaCacheRedisService) {
			((CaptchaCacheRedisService) ret).setStringRedisTemplate(redisTemplate);
		}
		return ret;
	}

	/**
	 * boot fat jar 中 PathMatchingResourcePatternResolver 无法枚举嵌套 jar 内的图片目录，
	 * 这里按 bg1..bgN 顺序直接读取并灌入 aj-captcha 图池，保证两种部署形态一致。
	 */
	@Bean
	public InitializingBean captchaBaseImageLoader(CaptchaService captchaService) {
		return () -> {
			Map<String, String> images = loadSequential(BASE_IMAGE_DIR, "bg");
			Map<String, String> blocks = loadSequential(BASE_BLOCK_DIR, "");
			if (images.isEmpty() || blocks.isEmpty()) {
				log.error("验证码底图或拼图模板为空，回退 aj-captcha 内置图池");
				ImageUtils.cacheImage(null, null, null);
				return;
			}
			ImageUtils.cacheBootImage(images, blocks, images);
			log.info("验证码底图池加载完成：底图 {} 张，拼图模板 {} 张", images.size(), blocks.size());
		};
	}

	private static Map<String, String> loadSequential(String directory, String prefix) {
		Map<String, String> images = new LinkedHashMap<>();
		ClassLoader classLoader = CaptchaConfig.class.getClassLoader();
		for (int i = 1; i <= MAX_BASE_IMAGES; i++) {
			String fileName = prefix + i + ".png";
			try (InputStream inputStream = classLoader.getResourceAsStream(directory + fileName)) {
				if (inputStream == null) {
					break;
				}
				images.put(fileName, Base64.getEncoder().encodeToString(inputStream.readAllBytes()));
			} catch (IOException e) {
				log.error("读取验证码图片失败：{}{}", directory, fileName, e);
				break;
			}
		}
		return images;
	}

	@Bean
	@ConditionalOnMissingBean
	public MessageSource messageSource() {
		ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasenames("captcha/messages", "captcha/messages");
		messageSource.setDefaultEncoding("UTF-8");
		return messageSource;
	}

}
