
package com.aryn.cloud.common.security.handler;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.support.SessionInvalidSupport;
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
		return Result.fail(e.getLocalizedMessage());
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
	 * 权限校验异常（缺少所需权限）
	 * @param e
	 * @author aryn
	 * @date 2025/8/1
	 * @return: com.aryn.cloud.common.core.util.Result
	 */
	@ExceptionHandler(NotPermissionException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Result handleNotPermissionException(NotPermissionException e) {
		log.warn("权限校验失败，缺少权限: {}", e.getMessage());
		return Result.fail(403, "权限不足，缺少所需权限: " + e.getMessage());
	}

	/**
	 * 角色校验异常（缺少所需角色）
	 * @param e
	 * @author aryn
	 * @date 2025/8/1
	 * @return: com.aryn.cloud.common.core.util.Result
	 */
	@ExceptionHandler(NotRoleException.class)
	@ResponseStatus(HttpStatus.FORBIDDEN)
	public Result handleNotRoleException(NotRoleException e) {
		log.warn("角色校验失败，缺少角色: {}", e.getMessage());
		return Result.fail(403, "权限不足，缺少所需角色: " + e.getMessage());
	}

	/**
	 * （拦截项目中的NotLoginException异常）
	 *
	 * <p>历史实现只按场景值返回中文 msg、且 code 固定为 1（与过滤器分支的 401 不一致），
	 * 前端因此"看着像登录态还活着"。现在统一为 {@code code=401 + data.reason}，与
	 * boot/cloud 过滤器出口同一份契约；msg 使用脱敏固定文案，不再回显 token。</p>
	 *
	 * @param nle 未登录异常
	 * @author 雨滴kian
	 * @date 2022/6/28
	 * @return: com.aryn.cloud.common.core.util.Result
	 */
	@ExceptionHandler(NotLoginException.class)
	public Result handlerNotLoginException(NotLoginException nle) {
		SessionInvalidReason reason = SessionInvalidReason.fromSaTokenType(nle.getType());
		log.warn("会话失效 reason={}", reason.name());
		return Result.fail(HttpStatus.UNAUTHORIZED.value(), reason.getMessage(),
				SessionInvalidSupport.resolveData(reason));
	}

}
