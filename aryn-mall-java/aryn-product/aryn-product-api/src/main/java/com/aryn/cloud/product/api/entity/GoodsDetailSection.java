package com.aryn.cloud.product.api.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商详结构化详情段落：标题 + 一段纯文本 + 可选一张图。
 *
 * <p>替代把产地、储存、场景全部塞进 HTML 描述的做法；运营可按块录入，
 * C 端按块渲染，缺图/缺文时只渲染存在的内容。</p>
 */
@Data
@NoArgsConstructor
public class GoodsDetailSection implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 段落标题，如「产地与采摘」 */
	private String title;

	/** 段落正文，纯文本，保留换行 */
	private String content;

	/** 配图 URL；空=纯文字段落 */
	private String imageUrl;
}
