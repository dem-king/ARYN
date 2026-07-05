
package com.aryn.cloud.common.security.annotation;

import java.lang.annotation.*;

/**
 * 防重提交注解
 *
 * <p>通过 AOP + Redis SETNX 实现，用于防止用户重复提交表单。
 *
 * <p>使用示例：
 * <pre>
 * &#64;PreventDuplicateSubmit(interval = 5, message = "请勿重复提交")
 * &#64;PostMapping("/create")
 * public Result create(@RequestBody OrderDTO dto) { ... }
 * </pre>
 *
 * @author aryn
 * @date 2026/7/4
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicateSubmit {

	/**
	 * 防重间隔时间（秒），默认 3 秒
	 */
	int interval() default 3;

	/**
	 * 提示消息
	 */
	String message() default "请勿重复提交";

}
