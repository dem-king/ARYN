package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 销售个人推广小程序码
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售个人推广小程序码")
public class SalesQrcodeVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "小程序码图片（data URI，可直接作为 image src）")
	private String qrcodeUrl;

	@Schema(description = "扫码进入的小程序页面路径")
	private String page;

	@Schema(description = "小程序码 scene 参数（sid=销售会员ID）")
	private String scene;

}
