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
  let raw = props.allowDetails ? stripUnsafeHtml(props.content || '') : String(props.content || '')
  // 兼容「##标题」无空格的 ATX 标题：在行首连续的 # 后补一个空格
  raw = raw.replace(/^(\s{0,3}#{1,6})([^\s#])/gm, '$1 $2')
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
.md {
  color: #1e293b;
  font-size: 14px;
  line-height: 1.7;
  word-wrap: break-word;
}
.md :deep(h1) {
  font-size: 18px;
  margin: 14px 0 10px;
  letter-spacing: -0.2px;
  color: #0f172a;
}
.md :deep(h2) {
  font-size: 15px;
  margin: 14px 0 8px;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 6px;
  border-left: 3px solid #6366f1;
  padding-left: 8px;
}
.md :deep(h3) {
  font-size: 14px;
  margin: 12px 0 6px;
  color: #1e293b;
}
.md :deep(p) {
  margin: 8px 0;
  line-height: 1.7;
  color: #334155;
}
.md :deep(strong) {
  font-weight: 600;
  color: #0f172a;
  background: linear-gradient(transparent 60%, #fef08a 60%);
  padding: 0 2px;
}
.md :deep(ul) {
  margin: 8px 0;
  padding-left: 18px;
}
.md :deep(li) {
  margin: 6px 0;
  color: #334155;
}
.md :deep(hr) {
  border: none;
  border-top: 1px dashed #cbd5e1;
  margin: 14px 0;
}
.md :deep(a) {
  color: var(--accent);
}
.md :deep(code) {
  background: #f1f5f9;
  border-radius: 4px;
  padding: 1px 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 13px;
  color: #be185d;
}
.md :deep(blockquote) {
  border-left: 3px solid #cbd5e1;
  padding: 4px 10px;
  margin: 8px 0;
  color: #475569;
  background: #f8fafc;
  border-radius: 0 6px 6px 0;
}
</style>
