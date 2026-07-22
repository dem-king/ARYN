package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 客服坐席配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_agent")
public class MessageAgent extends AbstractMessageEntity {

	private String staffId;
	private String enabled;
	private String autoAccept;
	private Integer maxActiveCount;
	private Integer currentActiveCount;
	private LocalDateTime lastAssignedTime;

	@Version
	private Integer version;

}
