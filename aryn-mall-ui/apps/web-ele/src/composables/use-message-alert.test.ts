import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  ALERT_DESKTOP_KEY,
  ALERT_SOUND_KEY,
  ALERT_VOICE_KEY,
  buildAlert,
  buildSpeech,
  clearPendingReplays,
  parseCardAmount,
  prebuiltVoiceFile,
  queueAudioReplay,
  readToggle,
  shouldSpeak,
  speak,
  unlockAudio,
  unreadIncrease,
  writeToggle,
} from './use-message-alert';

describe('unread increase detection', () => {
  it('treats the first snapshot as a baseline without alerting', () => {
    // 登录后拉到的存量未读不是「新消息」：首次必须只定基线
    expect(
      unreadIncrease(undefined, { conversationUnread: 5, noticeUnread: 3 }),
    ).toBeUndefined();
  });

  it('reports only the positive delta of each channel', () => {
    expect(
      unreadIncrease(
        { conversationUnread: 1, noticeUnread: 2 },
        { conversationUnread: 3, noticeUnread: 5 },
      ),
    ).toEqual({ conversationAdded: 2, noticeAdded: 3 });
  });

  it('stays silent when counts fall (marking read) or hold steady', () => {
    const previous = { conversationUnread: 4, noticeUnread: 4 };
    expect(
      unreadIncrease(previous, { conversationUnread: 1, noticeUnread: 2 }),
    ).toBeUndefined();
    expect(unreadIncrease(previous, previous)).toBeUndefined();
  });

  it('reports a single-channel rise while the other falls', () => {
    expect(
      unreadIncrease(
        { conversationUnread: 5, noticeUnread: 0 },
        { conversationUnread: 5, noticeUnread: 2 },
      ),
    ).toEqual({ conversationAdded: 0, noticeAdded: 2 });
  });
});

describe('alert copy', () => {
  it('prefers the conversation alert with its own title and body', () => {
    const payload = buildAlert({ conversationAdded: 1, noticeAdded: 2 }, [
      {
        kind: 'conversation',
        message: '在吗',
        title: '会员 member-9',
      },
    ]);

    // 有人在等回复比一条站内通知更急，会话优先
    expect(payload).toMatchObject({
      body: '在吗',
      count: 1,
      kind: 'conversation',
      title: '会员 member-9',
    });
  });

  it('falls back to counts when the matching item is absent', () => {
    expect(
      buildAlert({ conversationAdded: 0, noticeAdded: 3 }, []),
    ).toMatchObject({
      body: '收到 3 条新通知',
      count: 3,
      kind: 'notice',
      title: '新的站内通知',
    });
    expect(
      buildAlert({ conversationAdded: 2, noticeAdded: 0 }, []),
    ).toMatchObject({
      body: '有 2 个会话有新消息',
      count: 2,
      kind: 'conversation',
      title: '新的客服会话消息',
    });
  });

  it('carries amount and sourceType from the notice item for speech', () => {
    expect(
      buildAlert({ conversationAdded: 0, noticeAdded: 1 }, [
        {
          amount: '88.00',
          kind: 'notice',
          message: '订单 NO-1 金额 88.00 元',
          sourceType: 'ORDER_CREATE',
          title: '新订单待处理',
        },
      ]),
    ).toMatchObject({
      amount: '88.00',
      body: '订单 NO-1 金额 88.00 元',
      count: 1,
      kind: 'notice',
      sourceType: 'ORDER_CREATE',
      title: '新订单待处理',
    });
  });

  it('uses the notice item body for notice-only rises', () => {
    expect(
      buildAlert({ conversationAdded: 0, noticeAdded: 1 }, [
        { kind: 'notice', message: '订单 NO-1 已取消', title: '买家取消订单' },
      ]),
    ).toMatchObject({
      body: '订单 NO-1 已取消',
      kind: 'notice',
      title: '买家取消订单',
    });
  });
});

describe('alert toggles', () => {
  it('defaults to enabled only when the stored value is explicitly "0"', () => {
    localStorage.clear();
    expect(readToggle(ALERT_SOUND_KEY)).toBe(true);

    writeToggle(ALERT_SOUND_KEY, false);
    expect(readToggle(ALERT_SOUND_KEY)).toBe(false);

    writeToggle(ALERT_SOUND_KEY, true);
    expect(readToggle(ALERT_SOUND_KEY)).toBe(true);
  });

  it('keeps sound, voice and desktop switches independent', () => {
    localStorage.clear();
    writeToggle(ALERT_SOUND_KEY, false);
    writeToggle(ALERT_VOICE_KEY, true);
    writeToggle(ALERT_DESKTOP_KEY, true);

    expect(readToggle(ALERT_SOUND_KEY)).toBe(false);
    expect(readToggle(ALERT_VOICE_KEY)).toBe(true);
    expect(readToggle(ALERT_DESKTOP_KEY)).toBe(true);
  });
});

describe('card amount parsing', () => {
  it('reads the amount from the backend card payload', () => {
    expect(
      parseCardAmount('{"orderId":"1","title":"订单 NO-1","amount":"88.00"}'),
    ).toBe('88.00');
  });

  it('returns undefined for missing, blank or malformed payloads', () => {
    expect(parseCardAmount(undefined)).toBeUndefined();
    expect(parseCardAmount('')).toBeUndefined();
    expect(parseCardAmount('not-json')).toBeUndefined();
    expect(parseCardAmount('{"orderId":"1"}')).toBeUndefined();
    expect(parseCardAmount('{"amount":""}')).toBeUndefined();
    // amount 只在是字符串时采用，数字型不入播报口径（后端按字符串下发）
    expect(parseCardAmount('{"amount":88}')).toBeUndefined();
  });
});

describe('speech text', () => {
  it('announces a single order and asks for prompt handling', () => {
    expect(
      buildSpeech({
        amount: '88.00',
        body: '',
        count: 1,
        kind: 'notice',
        sourceType: 'ORDER_CREATE',
        title: '新订单待处理',
      }),
    ).toBe('您有一笔新订单，请及时处理');
  });

  it('merges multiple orders into a single generic reminder', () => {
    // 不分笔数：多条合并只提醒「有新的订单」，避免连单时念一堆数字
    expect(
      buildSpeech({
        amount: '88.00',
        body: '',
        count: 3,
        kind: 'notice',
        sourceType: 'ORDER_CREATE',
        title: '新订单待处理',
      }),
    ).toBe('您有新的订单，请及时处理');
  });

  it('keeps the cancel title so the source stays audible', () => {
    // 标题本身就是来源区分（买家取消/超时/管理端），换成通用文案会丢掉这个信息
    for (const title of [
      '买家取消订单',
      '订单超时自动取消',
      '管理端取消订单',
    ]) {
      expect(
        buildSpeech({
          body: '',
          count: 1,
          kind: 'notice',
          sourceType: 'ORDER_CANCEL',
          title,
        }),
      ).toBe(title);
    }
  });

  it('announces refund applications and COD reminders in business terms', () => {
    expect(
      buildSpeech({
        body: '',
        count: 1,
        kind: 'notice',
        sourceType: 'ORDER_REFUND_APPLY',
        title: '新的退款申请待审核',
      }),
    ).toBe('有新的退款申请待审核');
    expect(
      buildSpeech({
        body: '',
        count: 1,
        kind: 'notice',
        sourceType: 'COD_PAY_REMIND',
        title: '货到付款订单收款提醒',
      }),
    ).toBe('有货到付款订单待收款');
  });

  it('announces pending vessel bind applications with their apply source', () => {
    // 船员申请与业务员认领是同一 bizType，靠标题区分来源；语音必须沿用标题，
    // 否则运营只听到「有新的船舶绑定申请」，分不清该走哪套审核口径
    for (const title of ['有新的船员绑定申请', '有新的业务员认领船舶申请']) {
      expect(
        buildSpeech({
          body: '',
          count: 1,
          kind: 'notice',
          sourceType: 'VESSEL_BIND_APPLY_TODO',
          title,
        }),
      ).toBe(title);
    }
    // 连来多条时合并成通用提醒，不念数量
    expect(
      buildSpeech({
        body: '',
        count: 3,
        kind: 'notice',
        sourceType: 'VESSEL_BIND_APPLY_TODO',
        title: '有新的船员绑定申请',
      }),
    ).toBe('有新的船舶绑定申请待审核');
  });

  it('announces conversations and falls back to the title for unknown types', () => {
    expect(
      buildSpeech({
        body: '在吗',
        count: 1,
        kind: 'conversation',
        title: '会员 9',
      }),
    ).toBe('有新的客服消息，请及时回复');
    expect(
      buildSpeech({
        body: '',
        count: 1,
        kind: 'notice',
        sourceType: 'SOMETHING_ELSE',
        title: '系统通知',
      }),
    ).toBe('系统通知');
  });
});

describe('prebuilt voice files', () => {
  it('maps fixed copy to pre-generated audio files', () => {
    expect(prebuiltVoiceFile('您有一笔新订单，请及时处理')).toBe('order-1');
    expect(prebuiltVoiceFile('您有新的订单，请及时处理')).toBe('order-multi');
    expect(prebuiltVoiceFile('有新的退款申请待审核')).toBe('refund');
    expect(prebuiltVoiceFile('有货到付款订单待收款')).toBe('cod');
    expect(prebuiltVoiceFile('有新的客服消息，请及时回复')).toBe(
      'conversation',
    );
    expect(prebuiltVoiceFile('语音播报已开启')).toBe('voice-on');
  });

  it('maps every cancel source to its own audio file', () => {
    // 取消订单沿用标题（来源区分），三种来源各有一份音频
    expect(prebuiltVoiceFile('买家取消订单')).toBe('cancel-buyer');
    expect(prebuiltVoiceFile('订单超时自动取消')).toBe('cancel-timeout');
    expect(prebuiltVoiceFile('管理端取消订单')).toBe('cancel-admin');
  });

  it('maps vessel bind application copy to its audio files', () => {
    // 单条沿用标题区分来源，多条合并统一提醒不念数量
    expect(prebuiltVoiceFile('有新的船员绑定申请')).toBe('vessel-crew');
    expect(prebuiltVoiceFile('有新的业务员认领船舶申请')).toBe('vessel-sales');
    expect(prebuiltVoiceFile('有新的船舶绑定申请待审核')).toBe('vessel-multi');
  });

  it('returns undefined for dynamic copy that must fall back to TTS', () => {
    // 系统通知等动态标题没有现成音频
    expect(prebuiltVoiceFile('系统通知')).toBeUndefined();
    expect(prebuiltVoiceFile('您的订单已发货')).toBeUndefined();
  });
});

describe('speech throttling', () => {
  it('speaks again only after the minimum interval', () => {
    // 连单高峰逐条朗读等于机器枪，4 秒内的后续事件只更新铃铛
    expect(shouldSpeak(1000, 0)).toBe(false);
    expect(shouldSpeak(3999, 0)).toBe(false);
    expect(shouldSpeak(4000, 0)).toBe(true);
    expect(shouldSpeak(5000, 1000)).toBe(true);
  });

  it('honours a custom interval', () => {
    expect(shouldSpeak(1500, 1000, 400)).toBe(true);
    expect(shouldSpeak(1200, 1000, 400)).toBe(false);
  });
});

/**
 * 自动播放策略会拦住「还没点过页面」时的发声。
 *
 * 早期实现遇到这种情形是彻底静默：铃声没响、语音没念，只剩铃铛数字变化，
 * 管理员在别处忙时完全不知道来了新单。现在把这一声排队，等首次手势解锁后补播。
 */
describe('audio replay queue', () => {
  it('replays only the latest queued alert once audio unlocks', () => {
    clearPendingReplays();
    const first = vi.fn();
    const second = vi.fn();

    queueAudioReplay(first);
    queueAudioReplay(second);
    unlockAudio();

    // 只补播最近一条：积压连着响会变成噪音
    expect(first).not.toHaveBeenCalled();
    expect(second).toHaveBeenCalledTimes(1);

    // 队列消费完就清空，下一次手势不会重复补播
    unlockAudio();
    expect(second).toHaveBeenCalledTimes(1);
  });

  it('drops replays older than the TTL', () => {
    clearPendingReplays();
    const stale = vi.fn();
    queueAudioReplay(stale);

    // 超过 60s 的提醒已经过期，再响反而误导
    const now = Date.now();
    const spy = vi.spyOn(Date, 'now').mockReturnValue(now + 61_000);
    try {
      unlockAudio();
    } finally {
      spy.mockRestore();
    }

    expect(stale).not.toHaveBeenCalled();
  });
});

interface FakeUtteranceLike {
  emit: (type: string, event: { error?: string }) => void;
  lang: string;
  listeners: Record<string, Array<(event: { error?: string }) => void>>;
  rate: number;
  text: string;
  voice: unknown;
  volume: number;
}

function FakeSpeechUtterance(this: FakeUtteranceLike, text: string) {
  this.lang = '';
  this.listeners = {};
  this.rate = 1;
  this.text = text;
  this.voice = undefined;
  this.volume = 1;
}
FakeSpeechUtterance.prototype.addEventListener = function (
  this: FakeUtteranceLike,
  type: string,
  callback: (event: { error?: string }) => void,
) {
  (this.listeners[type] ??= []).push(callback);
};
FakeSpeechUtterance.prototype.emit = function (
  this: FakeUtteranceLike,
  type: string,
  event: { error?: string },
) {
  for (const callback of this.listeners[type] ?? []) {
    callback(event);
  }
};

/** 造一份可观测的 Web Speech 假实现：记录真正被交给浏览器的播报文本。 */
function installFakeSpeech(
  options: {
    rejectFirstError?: boolean;
    voices?: Array<{ lang: string; localService?: boolean; name: string }>;
  } = {},
) {
  const {
    rejectFirstError = false,
    voices = [{ lang: 'zh-CN', name: 'Tingting' }],
  } = options;
  const spoken: string[] = [];
  let attempts = 0;
  const synthesis = {
    cancel: vi.fn(),
    getVoices: () => voices,
    speak: vi.fn((utterance: FakeUtteranceLike) => {
      attempts += 1;
      // 模拟 Chrome 无手势时的行为：不抛异常，只异步派发 not-allowed
      if (rejectFirstError && attempts === 1) {
        queueMicrotask(() => utterance.emit('error', { error: 'not-allowed' }));
        return;
      }
      spoken.push(utterance.text);
    }),
  };
  Object.defineProperty(window, 'speechSynthesis', {
    configurable: true,
    value: synthesis,
  });
  Object.defineProperty(window, 'SpeechSynthesisUtterance', {
    configurable: true,
    value: FakeSpeechUtterance,
  });
  return { spoken, synthesis };
}

function setUserActivated(hasBeenActive: boolean) {
  Object.defineProperty(navigator, 'userActivation', {
    configurable: true,
    value: { hasBeenActive, isActive: false },
  });
}

function restoreProperty(
  target: object,
  key: string,
  descriptor?: PropertyDescriptor,
) {
  if (descriptor) {
    Object.defineProperty(target, key, descriptor);
    return;
  }
  Reflect.deleteProperty(target, key);
}

/**
 * 语音播报同样受浏览器手势策略限制。
 *
 * 真实缺陷（2026-10-18 管理端复现）：Chrome 155 里页面刚加载完、用户还没点过
 * 任何地方时，Web Speech 的 speak() 会以 error=not-allowed 拒绝且不抛异常；
 * 原来的实现没有接这个事件，于是「站内信到了，但一声不响」。修法与提示音一致：
 * 无手势先排队，首次手势后补播。
 */
describe('speech activation gate', () => {
  let speechDescriptor: PropertyDescriptor | undefined;
  let utteranceDescriptor: PropertyDescriptor | undefined;
  let activationDescriptor: PropertyDescriptor | undefined;

  beforeEach(() => {
    localStorage.clear();
    speechDescriptor = Object.getOwnPropertyDescriptor(
      window,
      'speechSynthesis',
    );
    utteranceDescriptor = Object.getOwnPropertyDescriptor(
      window,
      'SpeechSynthesisUtterance',
    );
    activationDescriptor = Object.getOwnPropertyDescriptor(
      navigator,
      'userActivation',
    );
    clearPendingReplays();
  });

  afterEach(() => {
    restoreProperty(window, 'speechSynthesis', speechDescriptor);
    restoreProperty(window, 'SpeechSynthesisUtterance', utteranceDescriptor);
    restoreProperty(navigator, 'userActivation', activationDescriptor);
    clearPendingReplays();
  });

  it('无手势时先排队，首次手势后才真正播报', () => {
    const { spoken, synthesis } = installFakeSpeech();
    setUserActivated(false);

    speak('您有一笔新订单，请及时处理');
    // 无手势时直接 speak 会被 Chrome 静默丢弃，必须排队
    expect(synthesis.speak).not.toHaveBeenCalled();

    setUserActivated(true);
    unlockAudio();
    expect(synthesis.speak).toHaveBeenCalledTimes(1);
    expect(spoken).toEqual(['您有一笔新订单，请及时处理']);

    // 补播只发生一次，下一次手势不会重复念
    unlockAudio();
    expect(synthesis.speak).toHaveBeenCalledTimes(1);
  });

  it('已有手势时立即播报，不进入排队', () => {
    const { spoken, synthesis } = installFakeSpeech();
    setUserActivated(true);

    speak('您有一笔新订单，请及时处理');
    expect(synthesis.speak).toHaveBeenCalledTimes(1);
    expect(spoken).toEqual(['您有一笔新订单，请及时处理']);
  });

  it('浏览器仍返回 not-allowed 时补播一次且不无限重试', async () => {
    const { spoken, synthesis } = installFakeSpeech({
      rejectFirstError: true,
    });
    // 模拟拿不到 userActivation 的旧浏览器：预检放行，只能靠 error 事件兜底
    setUserActivated(true);

    speak('有新的退款申请待审核');
    await Promise.resolve();
    expect(spoken).toEqual([]);

    unlockAudio();
    expect(synthesis.speak).toHaveBeenCalledTimes(2);
    expect(spoken).toEqual(['有新的退款申请待审核']);
  });

  it('补播超过 60 秒即过期，不再念过期订单', () => {
    const { synthesis } = installFakeSpeech();
    setUserActivated(false);

    speak('您有一笔新订单，请及时处理');
    expect(synthesis.speak).not.toHaveBeenCalled();

    const now = Date.now();
    const spy = vi.spyOn(Date, 'now').mockReturnValue(now + 61_000);
    try {
      setUserActivated(true);
      unlockAudio();
    } finally {
      spy.mockRestore();
    }
    expect(synthesis.speak).not.toHaveBeenCalled();
  });

  it('等待补播期间关掉语音开关就不再补念', () => {
    const { synthesis } = installFakeSpeech();
    setUserActivated(false);

    speak('您有一笔新订单，请及时处理');
    writeToggle(ALERT_VOICE_KEY, false);

    setUserActivated(true);
    unlockAudio();
    expect(synthesis.speak).not.toHaveBeenCalled();
  });

  it('登出清理会丢弃待补播的语音', () => {
    const { synthesis } = installFakeSpeech();
    setUserActivated(false);

    speak('您有一笔新订单，请及时处理');
    clearPendingReplays();

    setUserActivated(true);
    unlockAudio();
    expect(synthesis.speak).not.toHaveBeenCalled();
  });
});

/**
 * 音色选择：同是中文音色，神经/在线音质远好于本地基础音色。
 *
 * 原来「取第一个中文音色」可能随机拿到机械声；现在按可观测特征打分，
 * zh-CN、神经/在线、常见高质感音色名依次加分。
 */
describe('voice selection', () => {
  let speechDescriptor: PropertyDescriptor | undefined;
  let utteranceDescriptor: PropertyDescriptor | undefined;

  beforeEach(() => {
    speechDescriptor = Object.getOwnPropertyDescriptor(
      window,
      'speechSynthesis',
    );
    utteranceDescriptor = Object.getOwnPropertyDescriptor(
      window,
      'SpeechSynthesisUtterance',
    );
  });

  afterEach(() => {
    restoreProperty(window, 'speechSynthesis', speechDescriptor);
    restoreProperty(window, 'SpeechSynthesisUtterance', utteranceDescriptor);
  });

  it('prefers a natural zh-CN voice over the first-listed basic voice', () => {
    const { synthesis } = installFakeSpeech({
      voices: [
        { lang: 'zh-CN', name: 'Google 普通话（中国大陆）' },
        {
          lang: 'zh-CN',
          localService: false,
          name: 'Microsoft Xiaoxiao Neural',
        },
        { lang: 'zh-TW', name: 'Meijia' },
      ],
    });

    speak('您有一笔新订单');
    const [utterance] = synthesis.speak.mock.calls[0] ?? [];
    expect(utterance?.voice).toMatchObject({
      name: 'Microsoft Xiaoxiao Neural',
    });
    expect(utterance?.lang).toBe('zh-CN');
  });

  it('falls back to the only zh voice when no premium voice exists', () => {
    const { synthesis } = installFakeSpeech({
      voices: [{ lang: 'zh-TW', name: 'Yue' }],
    });

    speak('有新的客服消息');
    const [utterance] = synthesis.speak.mock.calls[0] ?? [];
    expect(utterance?.voice).toMatchObject({ name: 'Yue' });
  });
});
