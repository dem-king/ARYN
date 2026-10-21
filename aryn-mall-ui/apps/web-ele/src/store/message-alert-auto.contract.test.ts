import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import process from 'node:process';

import { describe, expect, it } from 'vitest';

/**
 * 「新订单到达就自动响」的契约守门。
 *
 * 真实缺陷：浏览器 autoplay 策略要求音频先在用户手势里解锁，早期实现只在
 * 「切换声音开关」时解锁 —— 于是刚打开后台、什么都没点过的管理员，
 * 第一条新订单是**静默**的（要等他自己点过页面才响）。
 *
 * 修法是 store 启动时挂上首次任意手势解锁监听。本契约钉住三件事：
 * 1. 布局启动走 start()（内含 armAudioUnlock），而不是只 refresh + connect；
 * 2. 语音播报按后端 bizType 说业务话术，且带节流；
 * 3. 语音开关与声音/桌面开关彼此独立。
 */
// vitest 从 aryn-mall-ui 根启动，故 cwd 即仓库 UI 根（与 code-review-contract 同口径）
const uiRoot = process.cwd();

function source(relativePath: string) {
  return readFileSync(resolve(uiRoot, 'apps/web-ele', relativePath), 'utf8');
}

const STORE = 'src/store/message.ts';
const ALERT = 'src/composables/use-message-alert.ts';
const LAYOUT = 'src/layouts/basic.vue';

function bellSource() {
  return readFileSync(
    resolve(
      uiRoot,
      'packages/effects/layouts/src/widgets/notification/notification.vue',
    ),
    'utf8',
  );
}

describe('auto-alert unlock contract', () => {
  it('布局启动走 start()，由它挂手势解锁（否则没点过页面的管理员漏首条）', () => {
    const layout = source(LAYOUT);
    const store = source(STORE);

    expect(layout).toContain('messageStore.start()');
    expect(store).toContain('armAudioUnlock()');
    // start 必须同时做三件事：解锁音频、拉基线、连推送
    expect(store).toMatch(
      /function start\(\)[\s\S]*?armAudioUnlock\(\)[\s\S]*?refreshNotifications\(\)[\s\S]*?connect\(\)/,
    );
  });

  it('手势解锁监听覆盖点击/按键/触摸且可卸载', () => {
    const alert = source(ALERT);

    for (const event of ['click', 'keydown', 'pointerdown', 'touchstart']) {
      expect(alert).toContain(`'${event}'`);
    }
    expect(alert).toContain('detachAudioUnlock');
    // 登出要卸载监听，避免常驻无用 handler
    expect(source(STORE)).toContain('detachAudioUnlock');
  });

  it('语音播报按 bizType 说业务话术并带节流', () => {
    const store = source(STORE);
    const alert = source(ALERT);

    // 播报文案来自 bizType，不是照念站内信标题
    expect(alert).toContain('ORDER_CREATE');
    expect(alert).toContain('ORDER_CANCEL');
    expect(alert).toContain('ORDER_REFUND_APPLY');
    expect(alert).toContain('COD_PAY_REMIND');
    // 节流：连单高峰不能逐条朗读
    expect(store).toContain('shouldSpeak');
    expect(store).toContain('lastSpokenAt');
  });

  it('站内信条目带上 sourceType 与金额，播报才有话可说', () => {
    const store = source(STORE);

    expect(store).toContain('parseCardAmount(item.cardPayload)');
    expect(store).toContain('sourceType: item.sourceType');
  });

  it('语音被浏览器手势策略拦下时排队补播，而不是永久静默', () => {
    const alert = source(ALERT);

    // 实测 Chrome 155：页面无用户手势时 speak() 只派发 error=not-allowed、不抛异常，
    // 不接这个事件就等于「站内信到了却一声不响」；必须与提示音同款补播队列
    expect(alert).toContain("'not-allowed'");
    expect(alert).toContain('queueSpeechReplay');
    expect(alert).toContain('flushPendingSpeeches');
    expect(alert).toContain('hasUserActivation');
    // 首次手势解锁时同时补播音频与语音
    expect(alert).toMatch(
      /function unlockAudio[\s\S]*?flushPendingReplays\(\)[\s\S]*?flushPendingSpeeches\(\)/,
    );
  });

  it('三个开关独立且都持久化', () => {
    const store = source(STORE);
    const layout = source(LAYOUT);
    const bell = bellSource();

    expect(store).toContain('ALERT_SOUND_KEY');
    expect(store).toContain('ALERT_VOICE_KEY');
    expect(store).toContain('ALERT_DESKTOP_KEY');
    // store 提供 setter，布局把开关值向下传、把事件向上转
    expect(store).toContain('voiceEnabled');
    expect(store).toContain('setVoiceEnabled');
    expect(layout).toContain('@toggle-voice="messageStore.setVoiceEnabled"');
    expect(layout).toContain(':voice-enabled="messageStore.voiceEnabled"');
    expect(bell).toContain("emit('toggleVoice'");
    expect(bell).toContain('alertVoice');
  });
});
