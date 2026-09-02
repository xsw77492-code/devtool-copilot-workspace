import type { GlobalThemeOverrides } from 'naive-ui'

export function buildThemeOverrides(primaryColor: string): GlobalThemeOverrides {
  return {
    common: {
      primaryColor,
      primaryColorHover: primaryColor,
      primaryColorPressed: primaryColor,
      primaryColorSuppl: primaryColor,
      borderRadius: '6px',
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
      color: '#ffffff',
      borderRadius: '8px'
    },
    Input: {
      borderRadius: '6px'
    },
    Button: {
      borderRadiusMedium: '6px',
      borderRadiusSmall: '5px'
    },
    Menu: {
      itemBorderRadius: '6px'
    }
  }
}
