package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 图片像素宽高（远程解析结果）。
 *
 * <p>C 端商品卡用宽高锁住图片占位比例，避免图片按原图比例加载后撑高卡片、
 * 导致瀑布流列表跳动。宽度/高度任一缺失时调用方按 1:1 兜底。</p>
 *
 * @author aryn
 * @since 2026/11/08
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图片像素宽高")
public class RemoteImageSizeVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "宽（像素）")
	private Integer width;

	@Schema(description = "高（像素）")
	private Integer height;

}
