
package com.aryn.cloud.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 全局异常处理器
 *
 * @author 雨滴kian
 * @version 1.0
 * @date 2022/5/3 14:12
 */
@Slf4j
@RestControllerAdvice
public class ArynGlobalExceptionHandlerResolver {

	/**
	 * 全局异常
	 *
	 * @author 雨滴kian
	 * @date 2022/5/3 14:15
	 * @version 1.0
	 */
	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public Result handleGlobalException(Exception e) {
		log.error("全局异常信息 ex={}", e.getMessage(), e);
		// 屏蔽内部敏感信息（如表名、SQL片段等），仅返回通用提示
		return Result.fail("系统繁忙，请稍后重试");
	}

	/**
	 * 结构体参数校验异常
	 *
	 * @author 雨滴kian
	 * @date 2022/5/3 14:15
	 * @version 1.0
	 */
	@ExceptionHandler(BindException.class)
	public Result bindException(BindException e) {
		log.warn("BindException:", e);
		// 拿到@NotNull,@NotBlank和 @NotEmpty等注解上的message值
		String msg = Objects.requireNonNull(e.getBindingResult().getFieldError()).getDefaultMessage();
		if (StrUtil.isNotEmpty(msg)) {
			// 自定义状态返回
			return Result.fail(msg);
		}

		return Result.fail(e.getMessage());
	}

	/**
	 * 自定义异常
	 * @param e
	 * @author 雨滴kian
	 * @date 2022/9/20
	 * @return: com.aryn.cloud.common.core.util.Result
	 */
	@ExceptionHandler(ArynBusinessException.class)
	@ResponseBody
	public Result error(ArynBusinessException e) {
		log.error("自定义异常信息 ex={}", e.getMsg(), e);
		return Result.fail(e.getCode(), e.getMsg());
	}

	/**
	 * （拦截项目中的NotLoginException异常）
	 * @param nle
	 * @author 雨滴kian
	 * @date 2022/6/28
	 * @return: com.aryn.cloud.common.core.util.Result
	 */
	@ExceptionHandler(NotLoginException.class)
	public Result handlerNotLoginException(NotLoginException nle) {
		// 判断场景值，定制化异常信息
		String message = "";
		if (nle.getType().equals(NotLoginException.NOT_TOKEN)) {
			message = "未提供token";
		}
		else if (nle.getType().equals(NotLoginException.INVALID_TOKEN)) {
			message = "token无效";
		}
		else if (nle.getType().equals(NotLoginException.TOKEN_TIMEOUT)) {
			message = "token已过期";
		}
		else if (nle.getType().equals(NotLoginException.BE_REPLACED)) {
			message = "token已被顶下线";
		}
		else if (nle.getType().equals(NotLoginException.KICK_OUT)) {
			message = "token已被踢下线";
		}
		else {
			message = "当前会话未登录";
		}
		return Result.fail(message);
	}

}
