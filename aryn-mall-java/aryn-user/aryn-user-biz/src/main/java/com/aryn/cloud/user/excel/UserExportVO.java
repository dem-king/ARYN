
package com.aryn.cloud.user.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 会员导出VO
 *
 * @author aryn
 * @since 2026/7/5
 */
@Data
@ColumnWidth(20)
public class UserExportVO {

	@ExcelProperty("昵称")
	private String nickname;

	@ExcelProperty("手机号")
	private String phone;

	@ExcelProperty("性别")
	private String sex;

	@ExcelProperty("所在城市")
	private String city;

	@ExcelProperty("所在省份")
	private String province;

	@ExcelProperty("用户来源")
	private String userSource;

	@ExcelProperty("积分余额")
	private Integer point;

	@ExcelProperty("储值余额")
	private BigDecimal balance;

	@ExcelProperty("累计消费金额")
	private BigDecimal totalConsume;

	@ExcelProperty("注册时间")
	@ColumnWidth(25)
	private String createTime;

}