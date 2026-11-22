package com.aryn.cloud.promotion.controller.admin;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.controller.app.AppCouponInfoController;
import com.aryn.cloud.promotion.controller.app.AppCouponUserController;
import com.aryn.cloud.promotion.controller.app.AppDistributionController;
import com.aryn.cloud.promotion.service.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** 实际 HTTP 写方法在委托业务服务前拒绝停运入口，不能依靠隐藏菜单。 */
class RetiredMarketingWriteEntryTest {

	@Test
	void seckillAndDiscountWritesAreClosed() {
		ISeckillActivityService seckill = mock(ISeckillActivityService.class);
		SeckillActivityController sc = new SeckillActivityController(seckill);
		rejected(() -> sc.add(null));
		rejected(() -> sc.edit(null));
		rejected(() -> sc.updateStatus("old", 1));
		IDiscountActivityService discount = mock(IDiscountActivityService.class);
		DiscountActivityController dc = new DiscountActivityController(discount);
		rejected(() -> dc.add(null));
		rejected(() -> dc.edit(null));
		rejected(() -> dc.updateStatus("old", 1));
		verifyNoInteractions(seckill, discount);
	}

	@Test
	void groupBuyAndDistributionCannotBeCreatedEditedOrEnabled() {
		IGroupBuyActivityService groupBuy = mock(IGroupBuyActivityService.class);
		GroupBuyActivityController gc = new GroupBuyActivityController(groupBuy);
		rejected(() -> gc.add(null));
		rejected(() -> gc.edit(null));
		IDistributionConfigService configs = mock(IDistributionConfigService.class);
		DistributionConfigController config = new DistributionConfigController(configs);
		rejected(() -> config.add(null));
		rejected(() -> config.edit(null));
		rejected(() -> config.enable("old"));
		verifyNoInteractions(groupBuy, configs);
	}

	@Test
	void couponCenterCannotExposeTemplatesOrIssueToAnyCaller() {
		ICouponInfoService info = mock(ICouponInfoService.class);
		ICouponUserService users = mock(ICouponUserService.class);
		AppCouponInfoController center = new AppCouponInfoController(info);
		rejected(() -> center.page(null, null, null));
		rejected(() -> center.byIds(java.util.List.of("template")));
		rejected(() -> new AppCouponUserController(users).page((com.aryn.cloud.promotion.api.entity.CouponUser) null));
		verifyNoInteractions(info, users);
	}

	@Test
	void socialDistributionRegistrationIsClosed() {
		IDistributionUserService users = mock(IDistributionUserService.class);
		AppDistributionController controller = new AppDistributionController(
			mock(IDistributionWithdrawService.class), mock(IDistributionCommissionFlowService.class), users,
			mock(IDistributionConfigService.class), mock(ISalesCustomerBindFacade.class));
		rejected(() -> controller.register(null));
		verifyNoInteractions(users);
	}

	private void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
		assertThatThrownBy(action).isInstanceOfSatisfying(ArynBusinessException.class,
			ex -> org.assertj.core.api.Assertions.assertThat(ex.getMsg()).contains("已停运"));
	}
}
