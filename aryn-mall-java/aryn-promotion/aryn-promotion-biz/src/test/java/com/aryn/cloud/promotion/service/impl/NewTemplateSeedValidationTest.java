package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模板市场新增装修模板（3 套 × 3 页）服务端校验自检。
 * <p>
 * 直接以文件绝对路径读取模板 JSON，复用 {@link DefaultPageDesignDocumentValidator} 按对应 pageType 校验，
 * 断言每张模板 ERROR 级别错误数为 0；WARNING 允许通过，但全部打印输出供人工审阅。
 * <p>
 * 本测试为只读自检，不修改任何模板与主代码，作为后续模板改动的回归基线。
 *
 * @author Doubao
 * @date 2026/10/02
 */
class NewTemplateSeedValidationTest {

	/** 9 张新模板所在目录（3 套 × home/category/usercenter）。 */
	private static final Path TEMPLATE_DIR = Path.of(
			"/Users/wangqingyu/Library/Application Support/DoubaoWork/Default/.doubaowork/agent_mode/workspace"
					+ "/.sessions/38445302313406722/agents/o_000cfZZ3Vt2/scratch/new-templates");

	/** 文件名 → 页面类型（pageType）：首页=1、分类页=3、个人中心页=4。 */
	private record Template(String fileName, String pageType) {
	}

	private static final List<Template> TEMPLATES = List.of(
			new Template("0720_ql_home.json", "1"),
			new Template("0721_ql_category.json", "3"),
			new Template("0722_ql_usercenter.json", "4"),
			new Template("0723_lt_home.json", "1"),
			new Template("0724_lt_category.json", "3"),
			new Template("0725_lt_usercenter.json", "4"),
			new Template("0726_zs_home.json", "1"),
			new Template("0727_zs_category.json", "3"),
			new Template("0728_zs_usercenter.json", "4"));

	private final DefaultPageDesignDocumentValidator validator = new DefaultPageDesignDocumentValidator();

	@Test
	void allNewTemplatesPassServerSideValidation() throws Exception {
		List<String> failureDetails = new ArrayList<>();
		System.out.println("================ 模板市场新增模板服务端校验明细 ================");

		for (Template template : TEMPLATES) {
			Path file = TEMPLATE_DIR.resolve(template.fileName());
			assertTrue(Files.exists(file), "模板文件不存在: " + file);

			String content = Files.readString(file, StandardCharsets.UTF_8);
			PageDesignValidationVO result = validator.validateStructured(content, template.pageType());

			List<PageDesignValidationVO.Issue> errors = result.getErrors();
			List<PageDesignValidationVO.Issue> warnings = result.getWarnings();
			PageDesignValidationVO.Budget budget = result.getPerformance();

			System.out.printf("%s (pageType=%s) -> ERROR=%d / WARNING=%d | 组件=%d 图片=%d 请求=%d 字节=%d%n",
					template.fileName(), template.pageType(), errors.size(), warnings.size(),
					budget.getComponentCount(), budget.getImageCount(), budget.getRequestCount(),
					budget.getContentBytes());

			for (PageDesignValidationVO.Issue issue : errors) {
				System.out.printf("    [ERROR ] code=%s message=%s componentId=%s componentType=%s field=%s%n",
						issue.getCode(), issue.getMessage(), issue.getComponentId(),
						issue.getComponentType(), issue.getField());
			}
			for (PageDesignValidationVO.Issue issue : warnings) {
				System.out.printf("    [WARNING] code=%s message=%s componentId=%s field=%s%n",
						issue.getCode(), issue.getMessage(), issue.getComponentId(), issue.getField());
			}

			if (!errors.isEmpty()) {
				failureDetails.add(template.fileName() + " 存在 " + errors.size() + " 个 ERROR: " + errors);
			}
		}

		System.out.println("================ 校验结束：共 " + TEMPLATES.size() + " 张模板 ================");
		assertEquals(0, failureDetails.size(),
				"以下模板存在阻断性错误，必须清零后才能发布:\n" + String.join("\n", failureDetails));
	}

}
