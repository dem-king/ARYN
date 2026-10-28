package com.aryn.cloud.auth.captcha;

import com.anji.captcha.config.AjCaptchaServiceAutoConfiguration;
import com.anji.captcha.model.common.CaptchaBaseMapEnum;
import com.anji.captcha.properties.AjCaptchaProperties;
import com.anji.captcha.util.ImageUtils;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 滑块验证码底图池契约。
 *
 * <p>前端把拖动距离按 310 的宽度还原成服务端坐标，并固定提交 y=5；
 * 而 y 是否固定取决于底图高度是否等于拼图模板高度 155。因此底图尺寸
 * 偏离 310×155 会让校验从“偶发失败”变成“必然失败”。</p>
 */
class CaptchaBaseImagePoolTest {

	private static final String ORIGINAL_IMAGES = "classpath*:captcha/jigsaw/original/*.png";

	@Test
	void baseImagePoolReplacesDefaultsWithSliderCompatibleImages() throws IOException {
		Resource[] resources = new PathMatchingResourcePatternResolver().getResources(ORIGINAL_IMAGES);

		assertThat(resources).hasSizeGreaterThanOrEqualTo(6);

		Set<String> names = new HashSet<>();
		for (Resource resource : resources) {
			BufferedImage image = ImageIO.read(resource.getInputStream());
			assertThat(image).as(resource.getFilename()).isNotNull();
			assertThat(image.getWidth()).as(resource.getFilename() + " 宽度").isEqualTo(310);
			assertThat(image.getHeight()).as(resource.getFilename() + " 高度").isEqualTo(155);
			assertThat(names.add(resource.getFilename())).as("底图文件名重复：" + resource.getFilename()).isTrue();
		}
	}

	@Test
	void slidingBlockTemplatesMatchJigsawGeometry() throws IOException {
		Resource[] resources = new PathMatchingResourcePatternResolver()
			.getResources("classpath*:captcha/jigsaw/slidingBlock/*.png");

		assertThat(resources).hasSizeGreaterThanOrEqualTo(1);

		for (Resource resource : resources) {
			BufferedImage image = ImageIO.read(resource.getInputStream());
			assertThat(image).as(resource.getFilename()).isNotNull();
			assertThat(image.getWidth()).as(resource.getFilename() + " 宽度").isEqualTo(47);
			assertThat(image.getHeight()).as(resource.getFilename() + " 高度").isEqualTo(155);
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void classpathJigsawPropertyReplacesBuiltInBaseImages() throws Exception {
		AjCaptchaProperties properties = new AjCaptchaProperties();
		properties.setJigsaw("classpath*:captcha/jigsaw");
		properties.setPicClick("classpath*:captcha/jigsaw/original");
		properties.setCacheType(AjCaptchaProperties.StorageType.other);

		new AjCaptchaServiceAutoConfiguration().captchaService(properties);

		Field field = ImageUtils.class.getDeclaredField("fileNameMap");
		field.setAccessible(true);
		Map<String, String[]> fileNameMap = (Map<String, String[]>) field.get(null);
		String[] originals = fileNameMap.get(CaptchaBaseMapEnum.ORIGINAL.getCodeValue());
		String[] picClick = fileNameMap.get(CaptchaBaseMapEnum.PIC_CLICK.getCodeValue());
		String[] blocks = fileNameMap.get(CaptchaBaseMapEnum.SLIDING_BLOCK.getCodeValue());

		assertThat(originals)
			.hasSizeGreaterThanOrEqualTo(6)
			.allMatch(name -> name.startsWith("bg"))
			.doesNotContain("1.png", "2.png", "3.png", "4.png", "5.png", "6.png");
		assertThat(picClick).hasSizeGreaterThanOrEqualTo(6).allMatch(name -> name.startsWith("bg"));
		assertThat(blocks).hasSizeGreaterThanOrEqualTo(1).contains("1.png", "6.png");
	}

}

