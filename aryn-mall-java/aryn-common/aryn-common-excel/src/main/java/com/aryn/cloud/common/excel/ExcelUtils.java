
package com.aryn.cloud.common.excel;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.aryn.cloud.common.excel.handler.ExcelImportListener;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * Excel导入导出工具类
 * <p>
 * 基于阿里 EasyExcel 封装，提供流式导出、分批导出和导入功能。
 * </p>
 *
 * @author aryn
 * @since 2026/7/5
 */
@Slf4j
public class ExcelUtils {

	private ExcelUtils() {
	}

	/**
	 * 流式导出：一次性写入所有数据 适合中小数据量（万级以下）
	 * @param response HTTP响应
	 * @param fileName 文件名（不含后缀）
	 * @param headClazz 导出类（需使用 @ExcelProperty 注解）
	 * @param data 导出数据
	 * @param <T> 数据类型
	 */
	public static <T> void export(HttpServletResponse response, String fileName, Class<T> headClazz, List<T> data) {
		setExportHeader(response, fileName);
		try {
			EasyExcel.write(response.getOutputStream(), headClazz).sheet(fileName).doWrite(data);
			log.info("Excel导出成功, fileName={}, 数据量={}", fileName, data.size());
		}
		catch (IOException ex) {
			log.error("Excel导出失败, fileName={}", fileName, ex);
			throw new RuntimeException("Excel导出失败", ex);
		}
	}

	/**
	 * 流式导出：分批查询写入，适合超大数据量（10w+） 使用 Supplier 分批获取数据，避免一次性加载到内存导致OOM
	 * @param response HTTP响应
	 * @param fileName 文件名（不含后缀）
	 * @param headClazz 导出类（需使用 @ExcelProperty 注解）
	 * @param supplier 数据提供者，每次调用返回一批数据，返回空列表表示结束
	 * @param <T> 数据类型
	 */
	public static <T> void export(HttpServletResponse response, String fileName, Class<T> headClazz,
			Supplier<List<T>> supplier) {
		setExportHeader(response, fileName);
		try {
			Supplier<Collection<?>> collectionSupplier = () -> new ArrayList<>(supplier.get());
			EasyExcel.write(response.getOutputStream(), headClazz).sheet(fileName).doWrite(collectionSupplier);
			log.info("Excel流式导出成功, fileName={}", fileName);
		}
		catch (IOException ex) {
			log.error("Excel流式导出失败, fileName={}", fileName, ex);
			throw new RuntimeException("Excel导出失败", ex);
		}
	}

	/**
	 * 导入：逐行校验 + 批量处理 使用 {@link PageReadListener} 逐批读取，默认每批500行
	 * @param file 上传的Excel文件
	 * @param headClazz 导入类（需使用 @ExcelProperty 注解）
	 * @param <T> 数据类型
	 * @return 导入的数据列表
	 */
	public static <T> List<T> importExcel(org.springframework.web.multipart.MultipartFile file, Class<T> headClazz) {
		return importExcel(file, headClazz, 500);
	}

	/**
	 * 导入：逐行校验 + 批量处理
	 * @param file 上传的Excel文件
	 * @param headClazz 导入类（需使用 @ExcelProperty 注解）
	 * @param batchSize 每批处理行数
	 * @param <T> 数据类型
	 * @return 导入的数据列表
	 */
	public static <T> List<T> importExcel(org.springframework.web.multipart.MultipartFile file, Class<T> headClazz,
			int batchSize) {
		List<T> allData = new ArrayList<>();
		try {
			EasyExcel.read(file.getInputStream(), headClazz, new PageReadListener<T>(dataList -> {
				log.info("Excel导入读取一批数据, 数量={}", dataList.size());
				allData.addAll(dataList);
			}, batchSize)).sheet().doRead();
			log.info("Excel导入成功, fileName={}, 总数据量={}", file.getOriginalFilename(), allData.size());
		}
		catch (IOException ex) {
			log.error("Excel导入失败, fileName={}", file.getOriginalFilename(), ex);
			throw new RuntimeException("Excel导入失败", ex);
		}
		return allData;
	}

	/**
	 * 导入：使用自定义 Listener 逐行校验 + 批量入库
	 * @param file 上传的Excel文件
	 * @param listener 自定义监听器
	 * @param <T> 数据类型
	 */
	public static <T> void importExcel(org.springframework.web.multipart.MultipartFile file,
			ExcelImportListener<T> listener) {
		try {
			EasyExcel.read(file.getInputStream(), listener.getHeadClazz(), listener).sheet().doRead();
			log.info("Excel导入(自定义Listener)成功, fileName={}", file.getOriginalFilename());
		}
		catch (IOException ex) {
			log.error("Excel导入失败, fileName={}", file.getOriginalFilename(), ex);
			throw new RuntimeException("Excel导入失败", ex);
		}
	}

	/**
	 * 下载导入模板
	 * @param response HTTP响应
	 * @param fileName 文件名（不含后缀）
	 * @param headClazz 模板类
	 * @param <T> 数据类型
	 */
	public static <T> void downloadTemplate(HttpServletResponse response, String fileName, Class<T> headClazz) {
		setExportHeader(response, fileName);
		try {
			EasyExcel.write(response.getOutputStream(), headClazz).sheet(fileName).doWrite(new ArrayList<>());
			log.info("Excel模板下载成功, fileName={}", fileName);
		}
		catch (IOException ex) {
			log.error("Excel模板下载失败, fileName={}", fileName, ex);
			throw new RuntimeException("Excel模板下载失败", ex);
		}
	}

	/**
	 * 设置导出响应头
	 */
	private static void setExportHeader(HttpServletResponse response, String fileName) {
		response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		response.setCharacterEncoding("utf-8");
		String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
		response.setHeader("Content-Disposition", "attachment;filename=" + encodedFileName + ".xlsx");
	}

}