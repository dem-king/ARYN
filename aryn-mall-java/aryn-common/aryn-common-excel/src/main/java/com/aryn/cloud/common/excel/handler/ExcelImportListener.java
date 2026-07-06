
package com.aryn.cloud.common.excel.handler;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel导入监听器基类 逐行校验 + 批量处理，避免大数据量导入时OOM
 * <p>
 * 子类需实现 {@link #validate} 和 {@link #saveBatch} 方法。
 * </p>
 *
 * @param <T> 导入数据类型
 * @author aryn
 * @since 2026/7/5
 */
@Slf4j
public abstract class ExcelImportListener<T> implements ReadListener<T> {

	/**
	 * 批处理阈值
	 */
	private static final int BATCH_SIZE = 500;

	/**
	 * 缓存的数据列表
	 */
	private final List<T> cachedDataList = new ArrayList<>(BATCH_SIZE);

	/**
	 * 校验失败的消息列表
	 */
	private final List<String> errorMessages = new ArrayList<>();

	/**
	 * 成功导入的行数
	 */
	private int successCount = 0;

	/**
	 * 获取导入数据的Class类型
	 * @return Class对象
	 */
	public abstract Class<T> getHeadClazz();

	/**
	 * 逐行校验
	 * @param data 当前行数据
	 * @param rowIndex 当前行号（从0开始）
	 * @return 校验结果，null表示通过，非null为错误信息
	 */
	protected abstract String validate(T data, int rowIndex);

	/**
	 * 批量保存
	 * @param data 一批校验通过的数据
	 */
	protected abstract void saveBatch(List<T> data);

	@Override
	public void invoke(T data, AnalysisContext context) {
		int rowIndex = context.readRowHolder().getRowIndex();
		String errorMsg = validate(data, rowIndex);
		if (errorMsg != null) {
			errorMessages.add("第" + (rowIndex + 1) + "行：" + errorMsg);
			return;
		}
		cachedDataList.add(data);
		if (cachedDataList.size() >= BATCH_SIZE) {
			saveBatchInternal();
		}
	}

	@Override
	public void doAfterAllAnalysed(AnalysisContext context) {
		saveBatchInternal();
		log.info("Excel导入处理完成, 成功行数={}, 失败行数={}", successCount, errorMessages.size());
		if (!errorMessages.isEmpty()) {
			log.warn("Excel导入校验失败信息: {}", String.join("; ", errorMessages));
		}
	}

	/**
	 * 执行批量保存
	 */
	private void saveBatchInternal() {
		if (cachedDataList.isEmpty()) {
			return;
		}
		saveBatch(cachedDataList);
		successCount += cachedDataList.size();
		cachedDataList.clear();
	}

	/**
	 * 获取校验失败信息
	 * @return 错误消息列表
	 */
	public List<String> getErrorMessages() {
		return errorMessages;
	}

	/**
	 * 获取成功导入行数
	 * @return 成功行数
	 */
	public int getSuccessCount() {
		return successCount;
	}

	/**
	 * 是否存在校验失败
	 * @return true表示存在失败
	 */
	public boolean hasErrors() {
		return !errorMessages.isEmpty();
	}

}