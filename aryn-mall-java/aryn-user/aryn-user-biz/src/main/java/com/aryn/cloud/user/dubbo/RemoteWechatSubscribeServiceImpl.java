package com.aryn.cloud.user.dubbo;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaSubscribeMessage;
import com.aryn.cloud.user.api.dto.WechatSubscribeMsgDTO;
import com.aryn.cloud.user.api.remote.RemoteWechatSubscribeService;
import com.aryn.cloud.user.config.WxMiniAppConfiguration;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;
import java.util.Map;

/**
 * 微信小程序订阅消息远程发送实现。
 * 持有 WxMaService 配置，通过 WxMaService.getSubscribeService().sendSubscribeMsg() 发送。
 */
@Slf4j
@DubboService
public class RemoteWechatSubscribeServiceImpl implements RemoteWechatSubscribeService {

	@Override
	public String sendSubscribeMessage(WechatSubscribeMsgDTO dto) {
		try {
			WxMaService wxMaService = WxMiniAppConfiguration.getMaService(dto.getAppId());
			WxMaSubscribeMessage.WxMaSubscribeMessageBuilder builder = WxMaSubscribeMessage.builder()
					.toUser(dto.getOpenid())
					.templateId(dto.getTemplateId());
			if (dto.getPage() != null && !dto.getPage().isEmpty()) {
				builder.page(dto.getPage());
			}
			WxMaSubscribeMessage message = builder.build();
			List<Map<String, String>> data = dto.getData();
			if (data != null) {
				for (Map<String, String> item : data) {
					message.addData(new WxMaSubscribeMessage.MsgData(item.get("key"), item.get("value")));
				}
			}
			wxMaService.getSubscribeService().sendSubscribeMsg(message);
			log.info("微信订阅消息发送成功 openid={} templateId={}", dto.getOpenid(), dto.getTemplateId());
			return "OK";
		}
		catch (WxErrorException e) {
			log.error("微信订阅消息发送失败 openid={} templateId={} errCode={} errMsg={}", dto.getOpenid(),
					dto.getTemplateId(), e.getError().getErrorCode(), e.getError().getErrorMsg(), e);
			return null;
		}
		catch (Exception e) {
			log.error("微信订阅消息发送异常 openid={} templateId={}", dto.getOpenid(), dto.getTemplateId(), e);
			return null;
		}
	}
}
