package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.vo.PageDesignValidationVO;

import java.util.List;

/**
 * 页面装修文档校验器。
 * <p>
 * {@link #validate} 返回阻断性错误消息列表，供发布流程使用；
 * {@link #validateStructured} 返回结构化错误、警告、业务引用与性能预算，供发布检查接口使用。
 *
 * @author 雨滴kian
 * @date 2026/09/05
 */
public interface PageDesignDocumentValidator {

	/**
	 * 校验页面内容，返回阻断性错误消息；空列表表示通过。
	 */
	default List<String> validate(String pageContent) {
		return validate(pageContent, null);
	}

	/**
	 * 校验页面内容，返回阻断性错误消息；空列表表示通过。
	 * <p>
	 * {@code pageType} 用于施加「该页面类型可放哪些组件」的白名单约束；
	 * 传 {@code null} 表示不限制（微页面/首页及存量调用）。
	 */
	List<String> validate(String pageContent, String pageType);

	/**
	 * 结构化校验：错误、警告、引用与性能预算。
	 */
	default PageDesignValidationVO validateStructured(String pageContent) {
		return validateStructured(pageContent, null);
	}

	/**
	 * 结构化校验：错误、警告、引用与性能预算。
	 * <p>
	 * {@code pageType} 语义同 {@link #validate(String, String)}。
	 */
	PageDesignValidationVO validateStructured(String pageContent, String pageType);

}
