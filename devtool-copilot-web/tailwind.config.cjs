/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  corePlugins: { preflight: false },
  theme: {
    extend: {
      colors: {
        bg0: 'var(--bg0)',
        bg1: 'var(--bg1)',
        panel: 'var(--panel2)',
        stroke: 'var(--stroke)',
        text: 'var(--text)',
        muted: 'var(--muted)',
        accent: 'var(--accent)',
        accent2: 'var(--accent2)'
      }
    }
  },
  plugins: []
}
