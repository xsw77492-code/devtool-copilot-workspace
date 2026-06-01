import type { GlobalThemeOverrides } from 'naive-ui'

export function buildThemeOverrides(primaryColor: string): GlobalThemeOverrides {
  return {
    common: {
      primaryColor,
      primaryColorHover: primaryColor,
      primaryColorPressed: primaryColor,
      primaryColorSuppl: primaryColor,
      borderRadius: '12px',
      fontFamily:
        'ui-sans-serif, system-ui, -apple-system, "Segoe UI Variable", "Segoe UI", Inter, "PingFang SC", "Microsoft YaHei", sans-serif',
      fontFamilyMono:
        'ui-monospace, "Cascadia Mono", "SFMono-Regular", Menlo, Monaco, Consolas, "Liberation Mono", monospace'
    },
    Layout: {
      color: 'transparent',
      siderColor: 'transparent'
    },
    Card: {
      color: 'rgba(255, 255, 255, 0.74)',
      borderRadius: '14px'
    },
    Input: {
      borderRadius: '12px'
    },
    Button: {
      borderRadiusMedium: '12px',
      borderRadiusSmall: '10px'
    },
    Menu: {
      itemBorderRadius: '10px'
    }
  }
}
