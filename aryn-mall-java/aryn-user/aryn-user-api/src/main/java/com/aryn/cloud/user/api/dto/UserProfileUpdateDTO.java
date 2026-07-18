package com.aryn.cloud.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserProfileUpdateDTO {

	@NotBlank(message = "昵称不能为空")
	@Size(max = 100, message = "昵称长度不能超过100个字符")
	private String nickname;

	@Pattern(regexp = "^[012]$", message = "性别值不正确")
	private String sex;

	@Size(max = 1024, message = "头像地址长度不能超过1024个字符")
	private String avatarUrl;

	@Size(max = 64, message = "城市长度不能超过64个字符")
	private String city;

	@Size(max = 64, message = "省份长度不能超过64个字符")
	private String province;

}
