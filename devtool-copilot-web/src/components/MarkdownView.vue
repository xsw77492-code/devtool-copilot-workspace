<script setup lang="ts">
import MarkdownIt from 'markdown-it'
import hljs from 'highlight.js'
import { computed } from 'vue'

const props = defineProps<{ content: string; allowDetails?: boolean }>()

function stripUnsafeHtml(input: string) {
  const s = String(input || '')
  const normalized = s
    .replace(/<\s*details\b[^>]*>/gi, '<details>')
    .replace(/<\s*\/\s*details\s*>/gi, '</details>')
    .replace(/<\s*summary\b[^>]*>/gi, '<summary>')
    .replace(/<\s*\/\s*summary\s*>/gi, '</summary>')
  const out = normalized.replace(/<(?!(\/?\s*(details|summary)\b))[^>]*>/gi, '')
  return out
}

const md = computed(() => {
  return new MarkdownIt({
    html: props.allowDetails === true,
    linkify: true
  })
})

const html = computed(() => {
  const inst = md.value
  inst.set({
    highlight: (code: string, lang?: string): string => {
      if (lang && hljs.getLanguage(lang)) {
        return `<pre class="hljs"><code class="hljs">${hljs.highlight(code, { language: lang }).value}</code></pre>`
      }
      return `<pre class="hljs"><code class="hljs">${inst.utils.escapeHtml(code)}</code></pre>`
    }
  })
  const raw = props.allowDetails ? stripUnsafeHtml(props.content || '') : String(props.content || '')
  return inst.render(raw)
})

async function fetchAuthedBlob(url: string) {
  const token = localStorage.getItem('dtc_token')
  const resp = await fetch(url, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined
  })
  if (!resp.ok) {
    let msg = ''
    try {
      msg = await resp.text()
    } catch {
    }
    throw new Error(msg || `下载失败(${resp.status})`)
  }
  const blob = await resp.blob()
  const cd = resp.headers.get('content-disposition') || ''
  const m = /filename\*\=UTF-8''([^;]+)/i.exec(cd)
  const filename = m ? decodeURIComponent(m[1]) : null
  return { blob, filename }
}

function clickAnchor(el: HTMLElement | null) {
  let cur: HTMLElement | null = el
  while (cur) {
    if (cur.tagName === 'A') return cur as HTMLAnchorElement
    cur = cur.parentElement
  }
  return null
}

function isAssetLink(href: string) {
  if (!href) return false
  return href.startsWith('/api/assets/') && (href.endsWith('/download') || href.endsWith('/preview'))
}

async function onClick(e: MouseEvent) {
  const a = clickAnchor(e.target as any)
  if (!a) return
  const href = a.getAttribute('href') || ''
  if (!isAssetLink(href)) return
  e.preventDefault()
  try {
    const { blob, filename } = await fetchAuthedBlob(href)
    const objUrl = URL.createObjectURL(blob)
    if (href.endsWith('/preview')) {
      window.open(objUrl, '_blank', 'noopener')
      setTimeout(() => URL.revokeObjectURL(objUrl), 60_000)
      return
    }
    const link = document.createElement('a')
    link.href = objUrl
    link.download = filename || 'file'
    document.body.appendChild(link)
    link.click()
    link.remove()
    setTimeout(() => URL.revokeObjectURL(objUrl), 10_000)
  } catch (err: any) {
    const msg = String(err?.message || '').trim()
    if (msg) window.alert(msg)
  }
}
</script>

<template>
  <div class="md" v-html="html" @click="onClick" />
</template>

<style scoped>
.md :deep(h1) {
  font-size: 18px;
  margin: 14px 0 10px;
  letter-spacing: -0.2px;
}
.md :deep(h2) {
  font-size: 14px;
  margin: 12px 0 8px;
}
.md :deep(p) {
  margin: 8px 0;
  line-height: 1.65;
}
.md :deep(ul) {
  margin: 8px 0;
  padding-left: 18px;
}
.md :deep(li) {
  margin: 6px 0;
}
.md :deep(a) {
  color: var(--accent);
}
</style>
