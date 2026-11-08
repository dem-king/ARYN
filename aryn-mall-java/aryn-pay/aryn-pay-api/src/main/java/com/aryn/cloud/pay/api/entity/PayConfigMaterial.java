
package com.aryn.cloud.pay.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 支付材料密文。明文仅存在于服务端受控内存，不写日志、不回显。
 */
@Data
@Schema(description = "支付材料密文")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "pay_config_material")
public class PayConfigMaterial extends Model<PayConfigMaterial> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "支付配置路由ID")
	private String configId;

	@Schema(description = "材料用途")
	private String purpose;

	@Schema(description = "密文（Base64）")
	private String ciphertext;

	@Schema(description = "GCM nonce（Base64）")
	private String nonce;

	@Schema(description = "加密密钥ID")
	private String keyId;

	@Schema(description = "加密算法")
	private String algorithm;

	@Schema(description = "明文 SHA-256 指纹")
	private String fingerprint;

	@Schema(description = "内容类型：PEM/DER/RAW")
	private String contentType;

	@Schema(description = "证书序列号")
	private String certSerialNo;

	@Schema(description = "证书生效时间")
	private LocalDateTime certNotBefore;

	@Schema(description = "证书到期时间")
	private LocalDateTime certNotAfter;

	@Schema(description = "明文字节数")
	private Integer byteSize;

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

	@Schema(description = "租户id")
	private String tenantId;

}
