import { describe, expect, it } from 'vitest'

import { defaultCommonStyle, getCommonStyle } from './common-style'

describe('getCommonStyle 圆角裁切', () => {
  /*
    装修组件的圆角「设置了不生效」的历史根因：纯图片/色块组件（图片魔方等）
    子内容贴满根节点，直角会盖住圆角弧线，必须由根节点裁切子内容。
  */
  it('任一角圆角大于 0 时输出 overflow hidden（裁掉盖住圆角的直角子内容）', () => {
    const style = getCommonStyle({
      ...defaultCommonStyle,
      styleLtRadius: 16,
    })

    expect(style.overflow).toBe('hidden')
  })

  it('四角全 0 时不裁切（不改变既有布局，避免误裁子元素阴影等溢出）', () => {
    const style = getCommonStyle({
      ...defaultCommonStyle,
      styleLtRadius: 0,
      styleRtRadius: 0,
      styleLbRadius: 0,
      styleRbRadius: 0,
    })

    expect(style.overflow).toBeUndefined()
  })

  it('圆角值以字符串传入时同样识别（历史数据兼容）', () => {
    const style = getCommonStyle({
      ...defaultCommonStyle,
      styleRbRadius: '12',
    })

    expect(style.overflow).toBe('hidden')
  })

  it('commonStyle 缺省时回落默认零值且不裁切', () => {
    const style = getCommonStyle(undefined)

    expect(style.borderTopLeftRadius).toBe('0px')
    expect(style.overflow).toBeUndefined()
  })
})
