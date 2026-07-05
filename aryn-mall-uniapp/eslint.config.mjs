import uni from '@uni-helper/eslint-config'

export default uni(
  {
    unocss: true,
    rules: {
      // 允许 warn/error 级别日志，但提示移除 log/debug
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'eslint-comments/no-unlimited-disable': 'off',
    },
  },
)
