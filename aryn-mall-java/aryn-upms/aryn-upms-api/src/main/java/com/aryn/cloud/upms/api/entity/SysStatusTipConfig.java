package com.aryn.cloud.upms.api.entity;

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

import java.time.LocalDateTime;

/**
 * 空态插画配置（租户级，一行一租户）。
 *
 * <p>背景：wot-design-uni 的 {@code wd-status-tip} 原本从 registry.npmmirror.com 拉预设图，
 * 该包被 npmmirror 的 unpkg 白名单拒绝（HTTP 451），C 端空态全部裂图。图片改为随
 * aryn-upms 静态资源发布后，本表提供租户级覆盖：运营在管理后台按预设位上传自己的图，
 * 未配置的位回落到内置静态图。</p>
 *
 * <p>存的是完整 URL（本地存储/MinIO/OSS 均可），与商品图同一套素材上传链路。</p>
 */
@Data
@Schema(description = "空态插画配置")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_status_tip_config")
public class SysStatusTipConfig extends Model<SysStatusTipConfig> {

	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "搜索无结果图 URL")
	private String searchUrl;

	@Schema(description = "网络异常图 URL")
	private String networkUrl;

	@Schema(description = "内容为空图 URL")
	private String contentUrl;

	@Schema(description = "收藏为空图 URL")
	private String collectUrl;

	@Schema(description = "评论为空图 URL")
	private String commentUrl;

	@Schema(description = "券/资产为空图 URL")
	private String haloUrl;

	@Schema(description = "消息为空图 URL")
	private String messageUrl;

	@Schema(description = "购物车空态图 URL")
	private String emptyCartUrl;

	@Schema(description = "订单列表空态图 URL")
	private String emptyOrderUrl;

	@Schema(description = "秒杀会场空态图 URL")
	private String emptySeckillUrl;

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
