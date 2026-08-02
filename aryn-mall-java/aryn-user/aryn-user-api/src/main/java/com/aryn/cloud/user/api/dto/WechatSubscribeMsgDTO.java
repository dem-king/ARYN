package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 微信小程序订阅消息发送 DTO。
 *
 * @param appId      小程序 AppID
 * @param openid     接收人 openid
 * @param templateId 模板 ID
 * @param page       跳转小程序页面路径（可选）
 * @param data       模板数据，key=关键词名 value=内容
 */
@Data
public class WechatSubscribeMsgDTO implements Serializable {

	private String appId;

	private String openid;

	private String templateId;

	private String page;

	private List<Map<String, String>> data;
}