package com.aryn.cloud.order.support;

import com.aryn.cloud.order.support.ChainOrderTextParser.ParsedItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接龙文本解析器测试。
 *
 * <p>核心用例是一条真实的船员报货接龙（2026-10 需求方提供原文），
 * 断言守住三类缺陷：
 * <ul>
 *   <li>规格里的数字被当成数量（「600ml*24瓶4箱」买的是 4 箱不是 24 瓶）；</li>
 *   <li>无分隔符连写的两个商品被吞成一个（「橙子4斤青岛清爽24罐装一箱」）；</li>
 *   <li>中文数字数量解析失败（「盐汽水四包」）。</li>
 * </ul>
 */
class ChainOrderTextParserTest {

	private static final String REAL_CHAIN_TEXT = """
			1. 任化东 红富士冰糖心4斤，香蕉（熟点）3斤，阳光玫瑰1串，麒麟无籽西瓜1个，卤牛腱子优质2斤，上海盐汽水600ml*24瓶4箱，花蛤5斤，
			2. 汤伟杰 青岛啤酒（冰淳）500ML12罐    2箱，黄金油蟠桃5斤，南瓜子1斤
			3. 机工卜兆土 香蕉3斤，橙子4斤青岛清爽24罐装一箱
			4. 水手长 盐汽水四包
			5. 三管 红富士冰糖心4斤 小番茄 红和黄 各一斤 花美人甜瓜 3斤 卷饼皮2包 拉面3斤
			6. 机工长  黄金油蟠桃3斤   带壳花生蒜香3斤  香蕉3斤（青）  蒜蓉辣椒酱2瓶
			7. 机工姜艳平 老冰糖2斤，青香蕉3斤。""";

	@DisplayName("真实接龙全文：行数与人名归属")
	@Test
	void parseRealChainPersonAndCount() {
		List<ParsedItem> items = ChainOrderTextParser.parse(REAL_CHAIN_TEXT);

		// 7 + 3 + 3 + 1 + 5 + 4 + 2 = 25 项
		assertEquals(25, items.size());
		assertEquals("任化东", items.get(0).personName());
		assertEquals("汤伟杰", items.get(7).personName());
		assertEquals("机工卜兆土", items.get(10).personName());
		assertEquals("水手长", items.get(13).personName());
		assertEquals("三管", items.get(14).personName());
		assertEquals("机工长", items.get(19).personName());
		assertEquals("机工姜艳平", items.get(23).personName());
	}

	@DisplayName("真实接龙全文：第 1 行规格数字不当数量、连写合并")
	@Test
	void parseRealChainLineOne() {
		List<ParsedItem> items = ChainOrderTextParser.parse(REAL_CHAIN_TEXT);

		assertItem(items.get(0), "红富士冰糖心", 4, "斤");
		assertItem(items.get(1), "香蕉（熟点）", 3, "斤");
		assertItem(items.get(2), "阳光玫瑰", 1, "串");
		assertItem(items.get(3), "麒麟无籽西瓜", 1, "个");
		assertItem(items.get(4), "卤牛腱子优质", 2, "斤");
		// 600ml*24瓶 是规格，4箱 才是数量
		assertItem(items.get(5), "上海盐汽水600ml*24瓶", 4, "箱");
		assertItem(items.get(6), "花蛤", 5, "斤");
	}

	@DisplayName("真实接龙全文：第 2-4 行（品牌规格连写、中文数字）")
	@Test
	void parseRealChainLineTwoToFour() {
		List<ParsedItem> items = ChainOrderTextParser.parse(REAL_CHAIN_TEXT);

		// 500ML12罐 是规格，2箱 是数量
		assertItem(items.get(7), "青岛啤酒（冰淳）500ML12罐", 2, "箱");
		assertItem(items.get(8), "黄金油蟠桃", 5, "斤");
		assertItem(items.get(9), "南瓜子", 1, "斤");
		assertItem(items.get(10), "香蕉", 3, "斤");
		// 「橙子4斤青岛清爽24罐装一箱」：无分隔符连写拆成两个商品，
		// 「24罐装」归入品名、数量取「一箱」
		assertItem(items.get(11), "橙子", 4, "斤");
		assertItem(items.get(12), "青岛清爽24罐装", 1, "箱");
		// 「四包」中文数字
		assertItem(items.get(13), "盐汽水", 4, "包");
	}

	@DisplayName("真实接龙全文：第 5-7 行（空格分段、「各」、括号规格）")
	@Test
	void parseRealChainLineFiveToSeven() {
		List<ParsedItem> items = ChainOrderTextParser.parse(REAL_CHAIN_TEXT);

		assertItem(items.get(14), "红富士冰糖心", 4, "斤");
		// 「小番茄 红和黄 各一斤」：拆不出红黄两行，品名去「各」并提示核对
		ParsedItem tomato = items.get(15);
		assertEquals("小番茄 红和黄", tomato.goodsName());
		assertEquals(1, tomato.quantity());
		assertNotNull(tomato.warning());
		assertItem(items.get(16), "花美人甜瓜", 3, "斤");
		assertItem(items.get(17), "卷饼皮", 2, "包");
		assertItem(items.get(18), "拉面", 3, "斤");
		assertItem(items.get(19), "黄金油蟠桃", 3, "斤");
		assertItem(items.get(20), "带壳花生蒜香", 3, "斤");
		assertItem(items.get(21), "香蕉（青）", 3, "斤");
		assertItem(items.get(22), "蒜蓉辣椒酱", 2, "瓶");
		assertItem(items.get(23), "老冰糖", 2, "斤");
		assertItem(items.get(24), "青香蕉", 3, "斤");
	}

	@DisplayName("先报名、换行报货的排版归属到暂存人名")
	@Test
	void parseNameLineThenGoodsLine() {
		String text = """
				1. 任化东
				红富士冰糖心4斤，香蕉3斤
				2. 汤伟杰 苹果2斤""";

		List<ParsedItem> items = ChainOrderTextParser.parse(text);

		assertEquals(3, items.size());
		assertEquals("任化东", items.get(0).personName());
		assertEquals("任化东", items.get(1).personName());
		assertEquals("汤伟杰", items.get(2).personName());
	}

	@DisplayName("没有报人名的行：数量缺失如实上报，不猜")
	@Test
	void parseMissingQuantityAndPerson() {
		List<ParsedItem> items = ChainOrderTextParser.parse("老冰糖");

		assertEquals(1, items.size());
		assertNull(items.get(0).quantity());
		assertEquals("老冰糖", items.get(0).goodsName());
		assertNotNull(items.get(0).warning());
	}

	@DisplayName("小数数量不猜进位：置空并提示人工调整")
	@Test
	void parseDecimalQuantity() {
		List<ParsedItem> items = ChainOrderTextParser.parse("1. 张三 猪肉1.5斤");

		assertEquals(1, items.size());
		assertEquals("张三", items.get(0).personName());
		assertEquals("猪肉", items.get(0).goodsName());
		assertNull(items.get(0).quantity());
		assertNotNull(items.get(0).warning());
	}

	@DisplayName("中文数字组合：十、两、二十、十五")
	@Test
	void parseChineseComposites() {
		List<ParsedItem> items = ChainOrderTextParser.parse("""
				1. 张三 啤酒十瓶
				2. 李四 袜子两双
				3. 王五 饮料二十瓶
				4. 赵六 面包十五包""");

		assertEquals(10, items.get(0).quantity());
		assertEquals(2, items.get(1).quantity());
		assertEquals(20, items.get(2).quantity());
		assertEquals(15, items.get(3).quantity());
	}

	@DisplayName("行首序号多种形态都能剥离")
	@Test
	void stripListMarkers() {
		List<ParsedItem> items = ChainOrderTextParser.parse("""
				1、张三 香蕉3斤
				（2）李四 苹果2斤
				③王五 橙子1斤""");

		assertEquals(3, items.size());
		assertEquals("张三", items.get(0).personName());
		assertEquals("李四", items.get(1).personName());
		assertEquals("王五", items.get(2).personName());
	}

	@DisplayName("整行只有人名不产出行")
	@Test
	void personOnlyLineProducesNothing() {
		List<ParsedItem> items = ChainOrderTextParser.parse("1. 任化东");

		assertTrue(items.isEmpty());
	}

	@DisplayName("空文本解析为空列表")
	@Test
	void parseBlankText() {
		assertTrue(ChainOrderTextParser.parse(null).isEmpty());
		assertTrue(ChainOrderTextParser.parse("   \n  ").isEmpty());
	}

	private void assertItem(ParsedItem item, String goodsName, int quantity, String unit) {
		assertEquals(goodsName, item.goodsName());
		assertEquals(quantity, item.quantity());
		assertEquals(unit, item.unit());
	}

}
