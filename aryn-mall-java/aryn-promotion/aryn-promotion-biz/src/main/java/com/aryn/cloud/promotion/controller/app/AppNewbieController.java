package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.vo.AppNewbieFloorVO;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;
import com.aryn.cloud.promotion.service.INewbieActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端新人专享楼层。
 *
 * <p>免登可访问（白名单双端登记）：未登录时楼层按潜在新人展示活动价做营销吸引；
 * 携带登录态时附带当前用户资格，展示端对已登录老客隐藏楼层/专区。价格始终取活动
 * 配置价（不按人出价），参与资格仍在下单/结算链路硬校验。
 *
 * @author aryn
 * @since 2026/10/13
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/newbie")
@Tag(description = "app-newbie", name = "C端新人专享")
public class AppNewbieController {

	private final INewbieActivityService newbieActivityService;

	/**
	 * 当前进行中的新人专享活动。
	 *
	 * <p>可选登录（与 /prices 同一语义）：未携带 token 时资格字段为 null，按潜在新人展示；
	 * 携带登录态时附带资格，页面据此对已登录老客隐藏楼层/专区。用户身份只从登录态读取。
	 */
	@Operation(summary = "当前进行中的新人专享活动（含新人价商品；无活动返回 null）")
	@GetMapping("/current")
	public Result<AppNewbieFloorVO> current(
			@RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
		ArynUser user = SecurityUtils.getUser();
		String userId = user != null ? user.getUserId() : null;
		return Result.success(newbieActivityService.getCurrentFloor(limit, userId));
	}

	/**
	 * 批量查询指定 SKU 的新人价（商详 / 购物车展示用）。
	 *
	 * <p>免登可访问（与楼层同一条白名单语义）：未登录返回活动价、资格字段为 null；
	 * 携带登录态时额外返回当前用户资格，页面据此在用户确定不符合资格时按原价展示并说明原因，
	 * 消除「页面标新人价、结算按原价」的价差观感。用户身份只从登录态读取，
	 * 不接受请求参数传入（否则改一个参数就能查别人的资格）。
	 */
	@Operation(summary = "指定SKU的新人价（可选登录，登录时附带当前用户资格）")
	@GetMapping("/prices")
	public Result<AppNewbiePriceVO> prices(@RequestParam("skuIds") List<String> skuIds) {
		ArynUser user = SecurityUtils.getUser();
		String userId = user != null ? user.getUserId() : null;
		return Result.success(newbieActivityService.getSkuPrices(skuIds, userId));
	}

}
