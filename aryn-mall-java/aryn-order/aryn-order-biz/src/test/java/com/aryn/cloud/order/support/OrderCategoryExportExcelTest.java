package com.aryn.cloud.order.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单导出（按分类分组）行组装的单元测试：写出后用 EasyExcel 读回逐行断言。
 *
 * <p>金额/数量单元格读回是字符串化的数字，统一转 {@link BigDecimal} 比较，
 * 避免受「10 / 10.0 / 10.00」单元格格式差异影响。
 */
class OrderCategoryExportExcelTest {

	private OrderInfo order(String orderNo, OrderItemEntity... items) {
		OrderInfo order = new OrderInfo();
		order.setOrderNo(orderNo);
		order.setOrderItemList(new ArrayList<>(List.of(items)));
		return order;
	}

	private OrderItemEntity item(String spuId, String spuName, String category, int quantity, String price) {
		OrderItemEntity item = new OrderItemEntity();
		item.setSpuId(spuId);
		item.setSpuName(spuName);
		item.setCategoryName(category);
		item.setBuyQuantity(quantity);
		item.setTotalPrice(new BigDecimal(price));
		return item;
	}

	private List<List<Map<Integer, String>>> writeAndRead(OrderInfo... orders) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		OrderCategoryExportExcel.write(List.of(orders), outputStream);

		List<List<Map<Integer, String>>> sheets = new ArrayList<>();
		sheets.add(readSheet(outputStream.toByteArray(), 0));
		sheets.add(readSheet(outputStream.toByteArray(), 1));
		sheets.add(readSheet(outputStream.toByteArray(), 2));
		return sheets;
	}

	private List<Map<Integer, String>> readSheet(byte[] bytes, int sheetNo) {
		List<Map<Integer, String>> rows = new ArrayList<>();
		EasyExcel.read(new ByteArrayInputStream(bytes), new AnalysisEventListener<Map<Integer, String>>() {
			@Override
			public void invoke(Map<Integer, String> row, AnalysisContext context) {
				rows.add(row);
			}

			@Override
			public void doAfterAllAnalysed(AnalysisContext context) {
			}
		}).sheet(sheetNo).headRowNumber(1).doRead();
		return rows;
	}

	private BigDecimal bd(Object cellValue) {
		return new BigDecimal(String.valueOf(cellValue));
	}

	@Test
	void detailRowsGroupByCategoryWithSubtotals() {
		// 故意乱序传入：导出须按分类分组排列（分类名 String 自然序），「未分类」固定排最后
		OrderInfo order = order("NO-1",
				item("spu-3", "薯片", "零食/膨化", 1, "12.00"),
				item("spu-2", "可乐", "饮品/碳酸", 2, "10.00"),
				item("spu-4", "纸巾", null, 1, "5.00"),
				item("spu-1", "矿泉水", "饮品/水", 3, "9.00"));

		List<List<Map<Integer, String>>> sheets = writeAndRead(order);
		List<Map<Integer, String>> detail = sheets.get(0);

		// 4 商品行 + 4 分类小计行（含未分类组） + 1 订单合计行
		assertThat(detail).hasSize(9);
		// 分类列（index 7）顺序：每个分类两行（商品+小计），合计行无分类
		assertThat(detail).extracting(row -> row.get(7))
			.containsExactly("零食/膨化", "零食/膨化", "饮品/水", "饮品/水", "饮品/碳酸", "饮品/碳酸",
					"未分类", "未分类", null);
		// 商品名列（index 8）：小计行、合计行的标记
		assertThat(detail).extracting(row -> row.get(8))
			.containsExactly("薯片", "零食/膨化 小计", "矿泉水", "饮品/水 小计", "可乐", "饮品/碳酸 小计",
					"纸巾", "未分类 小计", "订单合计");
		// 数量列（index 11）
		assertThat(detail).extracting(row -> bd(row.get(11)))
			.containsExactly(bd(1), bd(1), bd(3), bd(3), bd(2), bd(2), bd(1), bd(1), bd(7));
		// 金额列（index 12）：小计与合计
		assertThat(detail).extracting(row -> bd(row.get(12)))
			.containsExactly(bd("12"), bd("12"), bd("9"), bd("9"), bd("10"), bd("10"), bd("5"), bd("5"), bd("36"));
	}

	@Test
	void sceneColumnNeverBlankAndNullMeansPersonal() {
		// 场景列（index 2）：船供采购原样展示；历史数据未落场景（null）按个人购买展示，不得留空。
		// 小计行与订单合计行不复述订单级字段，故这两行的场景列按设计为空
		OrderInfo shipSupply = order("NO-2", item("spu-1", "缆绳", "五金", 1, "100.00"));
		shipSupply.setPurchaseScene("2");
		OrderInfo legacy = order("NO-3", item("spu-2", "矿泉水", "饮品/水", 1, "9.00"));

		List<List<Map<Integer, String>>> sheets = writeAndRead(shipSupply, legacy);
		List<Map<Integer, String>> detail = sheets.get(0);

		// 每单：商品行 + 分类小计行 + 订单合计行
		assertThat(detail).extracting(row -> row.get(2))
			.containsExactly("船供采购", null, null, "个人购买", null, null);
		// 商品行的场景不得为空字符串（留空会被运营读成「场景缺失」）
		assertThat(detail).extracting(row -> row.get(2))
			.doesNotContain("");
	}

	@Test
	void summarySheetAggregatesAcrossOrders() {
		OrderInfo orderA = order("NO-1",
				item("spu-2", "可乐", "饮品/碳酸", 2, "10.00"),
				item("spu-1", "矿泉水", "饮品/水", 3, "9.00"));
		OrderInfo orderB = order("NO-2",
				item("spu-5", "雪碧", "饮品/碳酸", 1, "5.00"),
				item("spu-3", "薯片", null, 1, "12.00"));

		List<List<Map<Integer, String>>> sheets = writeAndRead(orderA, orderB);
		List<Map<Integer, String>> summary = sheets.get(1);

		// 3 个分类
		assertThat(summary).hasSize(3);
		// 汇总行按总金额倒序：饮品/碳酸 15 → 未分类 12 → 饮品/水 9；列：分类、商品种数、总数量、总金额
		assertThat(summary).extracting(row -> row.get(0)).containsExactly("饮品/碳酸", "未分类", "饮品/水");
		assertThat(summary).extracting(row -> bd(row.get(1))).containsExactly(bd(2), bd(1), bd(1));
		assertThat(summary).extracting(row -> bd(row.get(2))).containsExactly(bd(3), bd(1), bd(3));
		assertThat(summary).extracting(row -> bd(row.get(3))).containsExactly(bd("15"), bd("12"), bd("9"));
	}

	@Test
	void orderSummarySheetBreaksDownByOrderAndCategory() {
		OrderInfo orderA = order("NO-1",
				item("spu-2", "可乐", "饮品/碳酸", 2, "10.00"),
				item("spu-1", "矿泉水", "饮品/水", 3, "9.00"),
				item("spu-6", "糖果", "零食", 1, "3.00"));
		OrderInfo orderB = order("NO-2",
				item("spu-5", "雪碧", "饮品/碳酸", 1, "5.00"),
				item("spu-3", "薯片", null, 1, "12.00"));

		List<List<Map<Integer, String>>> sheets = writeAndRead(orderA, orderB);
		List<Map<Integer, String>> perOrder = sheets.get(2);

		// 订单 A 3 个分类 + 订单 B 2 个分类，一行一订单一分类
		assertThat(perOrder).hasSize(5);
		// 行序 = 订单序（入参序），单内分类拼音序、「未分类」最后
		assertThat(perOrder).extracting(row -> row.get(0))
			.containsExactly("NO-1", "NO-1", "NO-1", "NO-2", "NO-2");
		assertThat(perOrder).extracting(row -> row.get(3))
			.containsExactly("零食", "饮品/水", "饮品/碳酸", "饮品/碳酸", "未分类");
		// 列：订单号、下单时间、收货人、分类、商品种数、总数量、总金额
		assertThat(perOrder).extracting(row -> bd(row.get(4)))
			.containsExactly(bd(1), bd(1), bd(1), bd(1), bd(1));
		assertThat(perOrder).extracting(row -> bd(row.get(5)))
			.containsExactly(bd(1), bd(3), bd(2), bd(1), bd(1));
		assertThat(perOrder).extracting(row -> bd(row.get(6)))
			.containsExactly(bd("3"), bd("9"), bd("10"), bd("5"), bd("12"));
	}

}
