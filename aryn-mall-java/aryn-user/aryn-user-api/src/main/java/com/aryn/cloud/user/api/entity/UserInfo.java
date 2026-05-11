
package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.common.core.annotation.Desensitization;
import com.aryn.cloud.common.core.desensitization.MobilePhoneDesensitization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商城用户
 *
 * @author 雨滴kian
 * @since 2022/2/22 14:28
 */
@Data
@Schema(description = "商城用户")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "user_info")
public class UserInfo extends Model<UserInfo> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "手机号")
	@Desensitization(MobilePhoneDesensitization.class)
	private String phone;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "性别：1、男；2、女；0、未知；")
	private String sex;

	@Schema(description = "头像")
	private String avatarUrl;

	@Schema(description = "所在城市")
	private String city;

	@Schema(description = "所在省份")
	private String province;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "用户来源")
	private String userSource;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "平台用户唯一ID（openid / alipay userId 等）")
	private String openId;

	@Schema(description = "会员等级ID")
	private String memberLevelId;

	@Schema(description = "积分余额")
	private Integer point = 0;

	@Schema(description = "储值余额")
	private BigDecimal balance = BigDecimal.ZERO;

	@Schema(description = "累计消费金额")
	private BigDecimal totalConsume = BigDecimal.ZERO;

}
