<script lang="ts" setup>
import { ref, watch } from 'vue';

import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
} from 'element-plus';

import { sanitizeCustomHtml } from './sanitize-custom-html';

interface CustomHtmlForm {
  height: number;
  html: string;
}

const props = defineProps<{ modelValue: CustomHtmlForm }>();
const emit = defineEmits(['update:modelValue']);

const defaultConfig: CustomHtmlForm = {
  html: '',
  height: 300,
};

const form = ref<CustomHtmlForm>({ ...defaultConfig, ...props.modelValue });

/**
 * 起步模板：只含白名单内的标签与行内样式，粘贴后两端渲染一致。
 * 结构类内容（规则清单、价目表）正是这个组件相对富文本的价值所在。
 */
const templates = [
  {
    label: '活动规则',
    html: `<h3 style="margin:0 0 12px;font-size:16px;font-weight:600;color:#1f2937;">活动规则</h3>
<ol style="margin:0;padding-left:20px;font-size:13px;line-height:1.9;color:#4b5563;">
  <li>活动时间：____ 年 __ 月 __ 日 至 ____ 年 __ 月 __ 日</li>
  <li>参与条件：登录会员参与，每人限 ____ 次</li>
  <li>优惠不与店铺其他活动叠加，退款后已享优惠不予返还</li>
  <li>本活动最终解释权归本店所有</li>
</ol>`,
  },
  {
    label: '对比表格',
    html: `<table border="1" cellpadding="8" cellspacing="0" style="width:100%;border-collapse:collapse;font-size:13px;color:#4b5563;">
  <tr style="background:#f3f4f6;">
    <th style="text-align:left;">项目</th>
    <th style="text-align:left;">说明</th>
  </tr>
  <tr><td>发货时效</td><td>付款后 48 小时内发出</td></tr>
  <tr><td>运费规则</td><td>满 ____ 元包邮</td></tr>
  <tr><td>售后说明</td><td>签收后 7 天内可申请</td></tr>
</table>`,
  },
  {
    label: '站内链接',
    html: `<p style="margin:0;font-size:14px;line-height:1.8;color:#4b5563;">
  订单进度请前往 <a href="/sub-pages/order/order-list/index" style="color:#1d4ed8;">我的订单</a>，账户与收货地址在 <a href="/pages/user/user-center/index" style="color:#1d4ed8;">个人中心</a>。
</p>`,
  },
];

function appendTemplate(html: string) {
  const current = form.value.html?.trim() ?? '';
  form.value.html = current ? `${current}\n${html}` : html;
}

watch(
  form,
  (val) => {
    // 保存到 schema 前自动净化 html 字段：移除 script / on* 事件 / javascript: 等危险内容
    emit('update:modelValue', {
      height: val.height,
      html: sanitizeCustomHtml(val.html ?? ''),
    });
  },
  { deep: true, immediate: true },
);
</script>

<template>
  <div class="setting-base">
    <div class="setting-title">
      <p>自定义HTML</p>
    </div>
    <div class="setting-form">
      <ElForm :model="form" label-position="top">
        <ElFormItem label="HTML 源码">
          <div class="custom-html-template-row">
            <span class="custom-html-template-label">插入模板</span>
            <ElButton
              v-for="tpl in templates"
              :key="tpl.label"
              link
              type="primary"
              @click="appendTemplate(tpl.html)"
            >
              {{ tpl.label }}
            </ElButton>
          </div>
          <ElInput
            v-model="form.html"
            :autosize="{ minRows: 10, maxRows: 20 }"
            maxlength="50000"
            placeholder="输入 HTML 源码，例如 &lt;div&gt;自定义区块&lt;/div&gt;"
            type="textarea"
          />
          <ElAlert class="xss-tip" :closable="false" show-icon type="info">
            <template #title>可用范围</template>
            <div>
              div / p / 标题 / 列表 / 表格 / 图片 / 链接 + 行内
              style；script、iframe、 表单、style 标签、背景图 url()
              会被自动过滤。
            </div>
            <div>
              站内跳转写 /pages 或 /sub-pages 路径（如
              /sub-pages/order/order-list/index）；http(s)
              外链在小程序内无法直接打开， 点击时会复制链接。
            </div>
          </ElAlert>
        </ElFormItem>
        <ElFormItem label="最小高度 (px)">
          <ElInputNumber
            v-model="form.height"
            :max="2000"
            :min="100"
            :step="10"
            controls-position="right"
          />
          <div class="custom-html-height-tip">
            内容超出时自动撑开，不会被裁切
          </div>
        </ElFormItem>
      </ElForm>
    </div>
  </div>
</template>

<style lang="scss">
@use '#/views/promotion/page-design/components/common/common.scss' as *;

.xss-tip {
  margin-top: 8px;
}

.custom-html-template-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 8px;
}

.custom-html-template-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.custom-html-height-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
