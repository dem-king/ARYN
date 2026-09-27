package com.aryn.cloud.promotion.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C 端领券列表可见性口径契约。
 *
 * <p>背景（2026-09-23 线上复现）：用户在首页装修的领券组件点「领取」后按钮变「已领取」，
 * 一刷新就退回「领取」，且唯一那张券会消失。两个独立缺陷叠加：
 *
 * <ol>
 *   <li>{@code selectCouponPage} 一律要求 {@code remain_num > 0}。券被领完（remain_num = 0）
 *       后整条记录被过滤，前端连「展示已领取」的机会都没有；</li>
 *   <li>前端 {@code couponInfo.getPage} 带了 {@code skipToken}，请求不带登录态，
 *       服务端 {@code StpUtil.isLogin()} 为 false、不回填 {@code userReceiveCount}，
 *       于是「已领取」状态无法在刷新后由服务端还原。</li>
 * </ol>
 *
 * <p>本测试守住服务端那一半：登录用户必须能看到自己已领完的券，
 * 且匿名（游客）列表仍只展示有库存的券，避免把领光的券暴露给游客。
 * 前端那一半由 {@code aryn-mall-uniapp/src/components/diy/coupon-received-state.test.ts}
 * 与 {@code retail-normalizers-extend.test.ts} 覆盖。
 */
class CouponVisibilityContractTest {

	private final Path mapper = findJavaRoot().resolve(
			"aryn-promotion/aryn-promotion-biz/src/main/resources/mapper/CouponInfoMapper.xml");

	@Test
	void couponPageKeepsClaimedCouponsVisibleWhenStockIsGone() throws IOException {
		String sql = selectStatement("selectCouponPage");

		// 登录用户：库存 > 0 或「本人已领取过」，两者取其一即展示
		assertThat(sql).contains("couponUser.userId");
		assertThat(sql).contains("EXISTS (");
		assertThat(sql).contains("FROM coupon_user AS coupon_user");
		assertThat(sql).contains("coupon_user.user_id = #{couponUser.userId}");
		assertThat(sql).contains("coupon_user.del_flag = '0'");
	}

	@Test
	void couponPageStillHidesSoldOutCouponsFromGuests() throws IOException {
		String sql = selectStatement("selectCouponPage");

		// 游客分支必须保留库存门槛，否则领光的券会向未登录用户泄露
		assertThat(sql).contains("couponUser.userId == null or couponUser.userId == ''");
		assertThat(sql).contains("AND coupon_info.`remain_num` > 0");
	}

	/**
	 * 领取记录的 EXISTS 子查询按 (coupon_id, user_id) 匹配；
	 * coupon_user 原只有 uk_coupon_user_source(tenant_id, user_id, ...)，
	 * 前缀对不上 coupon_id，缺索引会退化成全表扫描。
	 */
	@Test
	void claimedCouponLookupHasSupportingIndexInBothModes() throws IOException {
		Path javaRoot = findJavaRoot();
		for (Path schema : new Path[] {
				javaRoot.resolve("db/boot/82coupon_user_received_index.sql"),
				javaRoot.resolve("db/cloud/83coupon_user_received_index.sql"),
		}) {
			String sql = Files.readString(schema);
			assertThat(sql).as(schema.toString())
					.contains("idx_coupon_user_coupon_user", "(`coupon_id`, `user_id`)");
		}
	}

	private String selectStatement(String statementId) throws IOException {
		String xml = Files.readString(mapper);
		int start = xml.indexOf("id=\"" + statementId + "\"");
		assertThat(start).as("无法定位 Mapper 语句: " + statementId).isGreaterThanOrEqualTo(0);
		int end = xml.indexOf("</select>", start);
		return xml.substring(start, end);
	}

	private static Path findJavaRoot() {
		Path root = Path.of("").toAbsolutePath();
		while (root != null && !Files.exists(root.resolve("aryn-boot"))) {
			root = root.getParent();
		}
		assertThat(root).as("无法定位 aryn-mall-java").isNotNull();
		return root;
	}
}
