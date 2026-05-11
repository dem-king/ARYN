
package com.aryn.cloud.promotion.api.vo;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户领券记录
 *
 * @author 雨滴kian
 * @date 2022/9/23
 */
@Data
@Schema(description = "用户领券记录")
public class CouponUserVO implements Serializable {

	@Schema(description = "主键")
	private String id;

	/**
	 * 用户领取数量
	 */
	@TableField(exist = false)
	private Long userReceiveCount;

}
