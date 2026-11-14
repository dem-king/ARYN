package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 企业等级绑定（主体 → 当前企业等级）。
 *
 * <p>单写方：user 域等级重算服务维护（订单完成事件触发 + 月度对账 Job）。
 * 不在 buyer_org/vessel_info 上冗余 level_id——cloud 模式下主体表分属 order/vessel 库，
 * 跨域写会引入分布式一致性问题；跨域读统一走 RemoteEnterpriseUserService。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "企业等级绑定")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "enterprise_level_binding")
public class EnterpriseLevelBinding extends Model<EnterpriseLevelBinding> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "主体类型：1岸上企业 2船舶")
	private String subjectType;

	@Schema(description = "主体ID（buyer_org_id / vessel_id）")
	private String subjectId;

	@Schema(description = "当前企业等级ID（未定级为空）")
	private String levelId;

	@Schema(description = "租户ID")
	private String tenantId;

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

}
