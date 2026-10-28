package com.aryn.cloud.message.api.dto.notice;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台内部系统通知发送参数。
 *
 * <p>仅用于内部服务调用，租户由调用上下文决定，不接受外部传入。
 */
@Data
public class SystemNoticeSendDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String title;

	private String summary;

	private String content;

	private String category;

	private LocalDateTime expireTime;

}
