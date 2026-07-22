package com.aryn.cloud.message.api.dto.notice;

import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 通知草稿新增或编辑请求。 */
@Data
public class NoticeSaveRequest implements Serializable {

	@NotBlank
	@Size(max = 200)
	private String title;

	@Size(max = 500)
	private String summary;

	@NotBlank
	@Size(max = 10000)
	private String content;

	@NotBlank
	@Size(max = 64)
	private String category;

	@Size(max = 16)
	private String priority;

	@NotEmpty
	private Set<MessageIdentityType> targetTypes;

	private List<String> mallUserIds;
	private String memberLevelId;
	private String memberTagId;
	private List<String> staffUserIds;
	private List<String> roleIds;
	private List<String> deptIds;

	@Future
	private LocalDateTime expireTime;

	private NoticeJumpType jumpType;

	@Size(max = 2000)
	private String jumpPayload;

}
