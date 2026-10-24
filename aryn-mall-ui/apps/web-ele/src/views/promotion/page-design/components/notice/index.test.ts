import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import Notice from './index.vue';

const commonStyle = {
  styleTopMargin: 0,
  styleBottomMargin: 0,
  styleLeftMargin: 0,
  styleRightMargin: 0,
  styleTopPadding: 6,
  styleBottomPadding: 6,
  styleLeftPadding: 12,
  styleRightPadding: 12,
  styleLtRadius: 8,
  styleRtRadius: 8,
  styleLbRadius: 8,
  styleRbRadius: 8,
  bgColorDirection: 'to right',
  bgStartColor: 'rgba(255, 248, 233, 1)',
  bgEndColor: 'rgba(255, 248, 233, 1)',
  bgPicUrl: '',
};

function mountNotice(showData: Record<string, unknown> = {}) {
  return mount(Notice, {
    props: {
      showData: {
        color: 'rgba(100, 101, 102, 1)',
        contentList: [{ content: '公告一' }],
        commonStyle,
        ...showData,
      },
    },
  });
}

const goodsLink = {
  params: {},
  path: '',
  targetId: '1',
  type: 'goods',
};

describe('公告画布渲染', () => {
  it('没有链接时不显示右侧箭头', () => {
    expect(mountNotice().find('.notice-arrow').exists()).toBe(false);
  });

  it('内容项带装修链接时显示箭头', () => {
    const wrapper = mountNotice({
      contentList: [{ content: '公告一', link: goodsLink }],
    });
    expect(wrapper.find('.notice-arrow').exists()).toBe(true);
  });

  it('兼容旧数据 type=1 + url 的链接', () => {
    const wrapper = mountNotice({
      contentList: [{ content: '公告一', type: 1, url: '/pages/index/index' }],
    });
    expect(wrapper.find('.notice-arrow').exists()).toBe(true);
  });

  it('按 titleType 渲染文字或图片前缀', () => {
    const text = mountNotice({
      titleColor: '#f00',
      titleSize: 16,
      titleText: '公告',
      titleType: '2',
    });
    expect(text.find('.notice-icon--text').text()).toBe('公告');

    const image = mountNotice({
      titleSize: 16,
      titleType: '1',
      titleUrl: 'https://example.com/a.png',
    });
    const img = image.find('.notice-icon--img');
    expect(img.exists()).toBe(true);
    expect(img.attributes('style')).toContain('width: 16px');
  });
});
