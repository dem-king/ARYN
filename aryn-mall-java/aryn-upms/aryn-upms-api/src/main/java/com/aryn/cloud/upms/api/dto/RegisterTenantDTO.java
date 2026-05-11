
package com.aryn.cloud.upms.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 注册租户
 *
 * @author 雨滴kian
 * @date 2022/1115
 */
@Data
public class RegisterTenantDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * 公司名称
	 */
	private String name;

	/**
	 * 手机号
	 */
	private String phone;

	/**
	 * 套餐
	 */
	private String packageId;

}
