package com.aryn.cloud.order.support;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 订单导出（按分类分组）测试：行结构用 EasyExcel 读回断言，
 * 表头/分类色带/小计合计/数字格式等供应商报货相关样式用 POI 读回断言。
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
		item.setSalesPrice(new BigDecimal(price));
		item.setTotalPrice(new BigDecimal(price));
		return item;
	}

	private byte[] writeBytes(OrderInfo... orders) {
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		OrderCategoryExportExcel.write(List.of(orders), outputStream);
		return outputStream.toByteArray();
	}

	private List<List<Map<Integer, String>>> writeAndRead(OrderInfo... orders) {
		byte[] bytes = writeBytes(orders);

		List<List<Map<Integer, String>>> sheets = new ArrayList<>();
		sheets.add(readSheet(bytes, 0));
		sheets.add(readSheet(bytes, 1));
		sheets.add(readSheet(bytes, 2));
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
		// 单元格已套用两位小数格式，读回会是「3.00」；归一化后比较，避免 BigDecimal 的 scale 差异
		return new BigDecimal(String.valueOf(cellValue)).stripTrailingZeros();
	}

	private String fillHex(Cell cell) {
		XSSFColor color = (XSSFColor) cell.getCellStyle().getFillForegroundColorColor();
		return color == null ? null : color.getARGBHex();
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
	void deliveryWayColumnUsesUnifiedCompanyDeliveryLabels() {
		// 导出文案必须与 delivery_way 字典一致：3/4 都是公司配送，按目的地区分
		OrderInfo shore = order("NO-1", item("spu-1", "矿泉水", "饮品/水", 1, "9.00"));
		shore.setDeliveryWay("3");
		OrderInfo vessel = order("NO-2", item("spu-2", "缆绳", "五金", 1, "100.00"));
		vessel.setDeliveryWay("4");

		List<Map<Integer, String>> detail = writeAndRead(shore, vessel).get(0);

		// 每单：商品行 + 分类小计行 + 订单合计行；配送方式仅商品行展示
		assertThat(detail).extracting(row -> row.get(3))
			.containsExactly("公司配送（岸上）", null, null, "公司配送（到船）", null, null);
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

		// 3 个分类 + 1 行合计
		assertThat(summary).hasSize(4);
		// 汇总行按总金额倒序：饮品/碳酸 15 → 未分类 12 → 饮品/水 9；列：分类、商品种数、总数量、总金额
		assertThat(summary).extracting(row -> row.get(0)).containsExactly("饮品/碳酸", "未分类", "饮品/水", "合计");
		assertThat(summary).extracting(row -> bd(row.get(1))).containsExactly(bd(2), bd(1), bd(1), bd(4));
		assertThat(summary).extracting(row -> bd(row.get(2))).containsExactly(bd(3), bd(1), bd(3), bd(7));
		assertThat(summary).extracting(row -> bd(row.get(3))).containsExactly(bd("15"), bd("12"), bd("9"), bd("36"));
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

	@Test
	void detailSheetIsStyledForSupplierReading() throws Exception {
		OrderInfo order = order("NO-1",
				item("spu-1", "薯片", "零食/膨化", 1, "12.00"),
				item("spu-2", "可乐", "饮品/碳酸", 2, "10.00"),
				item("spu-3", "纸巾", null, 1, "5.00"));

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(writeBytes(order)))) {
			Sheet sheet = workbook.getSheet("订单明细");

			// 表头：深蓝底白字加粗，冻结表头与订单号列
			Cell header = sheet.getRow(0).getCell(0);
			assertThat(workbook.getFontAt(header.getCellStyle().getFontIndexAsInt()).getBold()).isTrue();
			assertThat(fillHex(header)).isEqualTo("FF305496");
			assertThat(sheet.getPaneInformation()).isNotNull();
			assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
			assertThat(sheet.getPaneInformation().getVerticalSplitPosition()).isEqualTo((short) 1);
			assertThat(sheet.getPaneInformation().getHorizontalSplitPosition()).isEqualTo((short) 1);

			// 地址列已放宽，不再截断；明细表保留筛选
			assertThat(sheet.getColumnWidth(6)).isGreaterThanOrEqualTo(30 * 256);
			assertThat(((XSSFSheet) sheet).getCTWorksheet().isSetAutoFilter()).isTrue();
			assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
			assertThat(sheet.getRepeatingRows()).isNotNull();

			// 行序：商品/小计交替（分类拼音序），最后订单合计
			// 1 零食商品、2 零食小计、3 饮品商品、4 饮品小计、5 未分类商品、6 未分类小计、7 订单合计
			assertThat(sheet.getRow(1).getCell(7).getStringCellValue()).isEqualTo("零食/膨化");
			assertThat(sheet.getRow(2).getCell(8).getStringCellValue()).isEqualTo("零食/膨化 小计");
			assertThat(sheet.getRow(7).getCell(8).getStringCellValue()).isEqualTo("订单合计");

			// 不同分类整行底色不同，未分类用独立底色；小计/合计灰底加粗
			String snackFill = fillHex(sheet.getRow(1).getCell(0));
			String drinkFill = fillHex(sheet.getRow(3).getCell(0));
			String uncategorizedFill = fillHex(sheet.getRow(5).getCell(0));
			assertThat(snackFill).isNotNull().isNotEqualTo("FFFFFFFF");
			assertThat(drinkFill).isNotEqualTo(snackFill);
			assertThat(uncategorizedFill).isNotEqualTo(snackFill).isNotEqualTo(drinkFill);
			// 分类名加粗，配合色带作为分组标识
			assertThat(workbook.getFontAt(sheet.getRow(1).getCell(7).getCellStyle().getFontIndexAsInt()).getBold())
				.isTrue();
			assertThat(fillHex(sheet.getRow(2).getCell(0))).isEqualTo("FFD9D9D9");
			assertThat(fillHex(sheet.getRow(7).getCell(0))).isEqualTo("FFA6A6A6");
			assertThat(workbook.getFontAt(sheet.getRow(2).getCell(8).getCellStyle().getFontIndexAsInt()).getBold())
				.isTrue();

			// 单价/金额按两位小数、数量按整数显示，且仍是数值单元格（可直接求和）
			Cell amount = sheet.getRow(1).getCell(12);
			assertThat(amount.getCellType()).isEqualTo(CellType.NUMERIC);
			assertThat(amount.getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
			assertThat(sheet.getRow(1).getCell(10).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
			assertThat(sheet.getRow(1).getCell(11).getCellStyle().getDataFormatString()).isEqualTo("#,##0");
		}
	}

	@Test
	void detailSheetMergesOrderLevelColumnsAndCategoryBlocks() throws Exception {
		OrderInfo orderA = order("NO-1",
				item("spu-1", "薯片", "零食/膨化", 1, "12.00"),
				item("spu-2", "矿泉水", "饮品/水", 3, "9.00"),
				item("spu-3", "可乐", "饮品/碳酸", 2, "10.00"));
		OrderInfo orderB = order("NO-2", item("spu-4", "缆绳", "五金", 1, "100.00"));

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(writeBytes(orderA, orderB)))) {
			// 明细表行结构：NO-1 三个分类块各占商品+小计两行（1-6）+ 订单合计行 7；NO-2 一块（8-9）+ 合计行 10
			Sheet detail = workbook.getSheet("订单明细");
			List<CellRangeAddress> merges = new ArrayList<>();
			for (int i = 0; i < detail.getNumMergedRegions(); i++) {
				merges.add(detail.getMergedRegion(i));
			}
			List<CellRangeAddress> expected = new ArrayList<>();
			// 订单级列（订单号~收货地址，0-6）整单合并，含分类小计行与订单合计行
			for (int column = 0; column <= 6; column++) {
				expected.add(new CellRangeAddress(1, 7, column, column));
				expected.add(new CellRangeAddress(8, 10, column, column));
			}
			// 分类列按分类块合并（商品行 + 收尾小计行）
			expected.add(new CellRangeAddress(1, 2, 7, 7));
			expected.add(new CellRangeAddress(3, 4, 7, 7));
			expected.add(new CellRangeAddress(5, 6, 7, 7));
			expected.add(new CellRangeAddress(8, 9, 7, 7));
			assertThat(merges).containsExactlyInAnyOrderElementsOf(expected);

			// 合并只是显示层：非首行单元格保留原值，筛选/透视仍按逐行取值匹配
			assertThat(detail.getRow(2).getCell(0).getStringCellValue()).isEqualTo("NO-1");
			assertThat(detail.getRow(7).getCell(0).getStringCellValue()).isEqualTo("NO-1");
			assertThat(detail.getRow(6).getCell(7).getStringCellValue()).isEqualTo("饮品/碳酸");
			assertThat(detail.getRow(9).getCell(0).getStringCellValue()).isEqualTo("NO-2");

			// 分类汇总表没有合并区域；订单分类小计表的订单级列（订单号/下单时间/收货人，0-2）
			// 按单合并，单分类订单单行不成区域
			assertThat(workbook.getSheet("分类汇总").getNumMergedRegions()).isZero();
			Sheet perOrder = workbook.getSheet("订单分类小计");
			List<CellRangeAddress> perOrderMerges = new ArrayList<>();
			for (int i = 0; i < perOrder.getNumMergedRegions(); i++) {
				perOrderMerges.add(perOrder.getMergedRegion(i));
			}
			assertThat(perOrderMerges).containsExactlyInAnyOrderElementsOf(List.of(
					new CellRangeAddress(1, 3, 0, 0),
					new CellRangeAddress(1, 3, 1, 1),
					new CellRangeAddress(1, 3, 2, 2)));
		}
	}

	@Test
	void summaryAndOrderSummarySheetsReuseCategoryColors() throws Exception {
		OrderInfo orderA = order("NO-1",
				item("spu-2", "可乐", "饮品/碳酸", 2, "10.00"),
				item("spu-1", "矿泉水", "饮品/水", 3, "9.00"),
				item("spu-6", "糖果", "零食", 1, "3.00"));
		OrderInfo orderB = order("NO-2", item("spu-5", "雪碧", "饮品/碳酸", 1, "5.00"));

		try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(writeBytes(orderA, orderB)))) {
			String snackFill = fillHex(workbook.getSheet("订单明细").getRow(1).getCell(0));

			// 分类汇总（第 0 行为表头）：分类单元格沿用明细表的分类底色，合计行灰底加粗
			// 汇总按金额倒序：饮品/碳酸 15、饮品/水 9、零食 3、合计
			Sheet summary = workbook.getSheet("分类汇总");
			assertThat(summary.getRow(3).getCell(0).getStringCellValue()).isEqualTo("零食");
			assertThat(fillHex(summary.getRow(3).getCell(0))).isEqualTo(snackFill);
			assertThat(summary.getRow(4).getCell(0).getStringCellValue()).isEqualTo("合计");
			assertThat(fillHex(summary.getRow(4).getCell(0))).isEqualTo("FFA6A6A6");
			assertThat(workbook.getFontAt(summary.getRow(4).getCell(0).getCellStyle().getFontIndexAsInt()).getBold())
				.isTrue();

			// 订单分类小计（第 0 行为表头）：每单最后一行的下边框加粗，便于按订单切分
			Sheet perOrder = workbook.getSheet("订单分类小计");
			assertThat(perOrder.getRow(1).getCell(3).getStringCellValue()).isEqualTo("零食");
			assertThat(perOrder.getRow(1).getCell(0).getCellStyle().getBorderBottom())
				.isEqualTo(BorderStyle.THIN);
			assertThat(perOrder.getRow(3).getCell(0).getCellStyle().getBorderBottom())
				.isEqualTo(BorderStyle.MEDIUM);
			assertThat(perOrder.getRow(4).getCell(0).getCellStyle().getBorderBottom())
				.isEqualTo(BorderStyle.MEDIUM);
		}
	}

}
