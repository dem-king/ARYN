package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.promotion.api.vo.MemberActivityGoodsVO;
import com.aryn.cloud.promotion.service.IMemberActivityGoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C 端会员专区自动数据源（装修多 Tab 的 {@code activity} 模式）。
 *
 * <p><b>免登</b>：只返回会员价活动的元信息与商品 SPU 列表，不含任何用户维度数据，
 * 与 {@code /app/member/prices} 的免登语义一致；登录态与否返回相同内容。
 *
 * <p>为什么不把价格混进本接口：装修楼层只需要「展示哪些商品」，价格由
 * {@code /app/member/prices} 按 SPU 批量查询（未登录返回活动配置价、带登录态返回资格与
 * 折扣率），两个接口职责分离，避免价格聚合口径重复实现。
 *
 * <p>activityId 语义（装修器保存的固定活动 vs 跟随当前活动）：
 * <ul>
 *   <li>不传：取当前进行中的会员价活动（已发布 + 时间窗内，优先级最高）——「跟着活动自动变」；</li>
 *   <li>传值：固定该活动（仅校验存在 + 会员价类型，不校验时间窗）——允许运营提前装修，
 *       也允许活动结束后保留楼层配置（此时价格接口无活动价，商品仍可展示）。</li>
 * </ul>
 *
 * <p>boot 模式：入口在 aryn-boot 的 {@code secure.ignore.urls} 白名单中
 * （{@code /app/member/activity-goods}，与 {@code /app/member/prices} 相邻登记）；
 * cloud 模式：网关白名单/路由在 Nacos {@code aryn-gateway-dev.yml} 中维护，
 * 由 db/cloud/165 号增量脚本登记 {@code /promotion/app/member/activity-goods}、
 * {@code /boot/app/member/activity-goods} 与精确路径路由（不使用 {@code /boot/app/member/**} 通配）。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/member")
@Tag(description = "app-member", name = "C端会员专区数据源")
public class AppMemberActivityGoodsController {

	private final IMemberActivityGoodsService memberActivityGoodsService;

	@Operation(summary = "会员专区自动数据源：会员价活动商品SPU列表（免登，activityId 可选）")
	@GetMapping("/activity-goods")
	public Result<MemberActivityGoodsVO> activityGoods(
			@RequestParam(value = "activityId", required = false) String activityId) {
		return Result.success(memberActivityGoodsService.getActivityGoods(activityId));
	}

}
