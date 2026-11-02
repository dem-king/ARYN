import { mount } from '@vue/test-utils';

import { describe, expect, it } from 'vitest';

import Notice from './index.vue';
import NoticeScroller from './notice-scroller.vue';

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

describe('公告画布滚动（与小程序实机一致）', () => {
  it('单条横向走无缝跑马灯（MarqueeText ticker），不再静态截断', () => {
    const wrapper = mountNotice();
    expect(wrapper.find('.strip-marquee').exists()).toBe(true);
    expect(wrapper.findComponent(NoticeScroller).exists()).toBe(false);
  });

  it('多条横向走 wd-notice-bar 同算法逐条滚动', () => {
    const wrapper = mountNotice({
      contentList: [{ content: '公告一' }, { content: '公告二' }],
    });
    expect(wrapper.find('.notice-scroller').exists()).toBe(true);
    expect(wrapper.find('.strip-marquee').exists()).toBe(false);
    const scroller = wrapper.findComponent(NoticeScroller);
    expect(scroller.props('direction')).toBe('horizontal');
    expect(scroller.props('scrollable')).toBe(true);
  });

  it('上下滚动走竖向轮播仿真', () => {
    const wrapper = mountNotice({
      direction: 'vertical',
      contentList: [{ content: '公告一' }, { content: '公告二' }],
    });
    expect(wrapper.findComponent(NoticeScroller).props('direction')).toBe(
      'vertical',
    );
  });

  it('单条上下滚动不动画、按配置换行（与 wd-notice-bar wrapable 一致）', () => {
    const wrapper = mountNotice({
      direction: 'vertical',
      contentList: [{ content: '公告一' }],
    });
    const scroller = wrapper.findComponent(NoticeScroller);
    expect(scroller.props('wrapable')).toBe(true);
    expect(scroller.props('scrollable')).toBe(false);
  });

  it('速度为 0 时回落默认 50（与 C 端 `Number(speed) || 50` 同口径）', () => {
    const wrapper = mountNotice({
      speed: 0,
      contentList: [{ content: '公告一' }, { content: '公告二' }],
    });
    expect(wrapper.findComponent(NoticeScroller).props('speed')).toBe(50);
  });

  it('公告内容全部为空白时显示占位提示（实机整块不渲染，画布保留可选中）', () => {
    const wrapper = mountNotice({ contentList: [{ content: '   ' }] });
    expect(wrapper.find('.notice-text').exists()).toBe(true);
    expect(wrapper.findComponent(NoticeScroller).exists()).toBe(false);
  });
});
