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
	List<String> validate(String pageContent);

	/**
	 * 结构化校验：错误、警告、引用与性能预算。
	 */
	PageDesignValidationVO validateStructured(String pageContent);

}
