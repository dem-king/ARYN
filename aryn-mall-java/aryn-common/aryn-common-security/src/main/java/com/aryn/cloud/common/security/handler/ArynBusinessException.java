
package com.aryn.cloud.common.security.handler;

import com.aryn.cloud.common.core.constant.CommonConstants;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自定义异常
 *
 * @author 雨滴kian
 * @date 2022/9/20
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ArynBusinessException extends RuntimeException {

	private Integer code;

	private String msg;

	public ArynBusinessException() {
		this.code = CommonConstants.FAIL;
	}

	public ArynBusinessException(String msg) {
		this.code = CommonConstants.FAIL;
		this.msg = msg;
	}

	public ArynBusinessException(Integer code, String msg) {
		this.code = code;
		this.msg = msg;
	}

}
