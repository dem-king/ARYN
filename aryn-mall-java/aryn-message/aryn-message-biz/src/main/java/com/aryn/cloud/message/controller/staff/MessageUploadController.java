package com.aryn.cloud.message.controller.staff;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.vo.conversation.MessageUploadVO;
import com.aryn.cloud.message.service.MessageUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 客服会话图片上传接口（消息专用，不依赖管理端素材库权限）。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/staff/upload")
@Tag(description = "message-upload", name = "客服消息上传")
public class MessageUploadController {

	private final MessageUploadService messageUploadService;

	@PostMapping("/image")
	@Operation(summary = "上传客服会话图片（jpg/jpeg/png/webp，≤5MB）")
	public Result<MessageUploadVO> uploadImage(
			@RequestPart(value = "file", required = false) MultipartFile file) {
		// 登录 TOB 即可：与发送消息接口同口径，私信页也复用本接口；不做素材库权限依赖。
		// file 缺失（required=false）交给服务层返回中文业务错误，避免 Spring 缺参异常被全局兜底成 500。
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOB);
		return Result.success(messageUploadService.uploadImage(user.getTenantId(), file));
	}

}
