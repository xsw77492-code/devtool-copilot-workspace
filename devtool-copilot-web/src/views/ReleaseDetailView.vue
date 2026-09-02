<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NDropdown, NInput, NSpin, useDialog, useMessage } from 'naive-ui'
import { releaseApi, type ProjectRelease } from '../api/release'
import { assetApi } from '../api/asset'
import { milestoneApi, type Milestone } from '../api/milestone'
import { projectCollabApi, type ProjectMemberRole } from '../api/projectCollab'
import { useAuthStore } from '../stores/auth'
import { useProjectStore } from '../stores/project'
import MarkdownView from '../components/MarkdownView.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const ps = useProjectStore()
const auth = useAuthStore()

const projectId = computed(() => Number(route.params.projectId))
const releaseId = computed(() => Number(route.params.releaseId))

const loading = ref(false)
const saving = ref(false)

const release = ref<ProjectRelease | null>(null)
const myRole = ref<ProjectMemberRole | null>(null)

const milestones = ref<Milestone[]>([])
const milestoneName = computed(() => {
  const mid = Number(release.value?.milestoneId || 0)
  if (!mid) return ''
  const m = milestones.value.find((x) => Number(x.id) === mid)
  return m?.name || `#${mid}`
})

const projectArchived = computed(() => {
  const p = ps.byId.get(Number(projectId.value || 0))
  return Number((p as any)?.archived || 0) === 1
})

const myUserId = computed(() => Number((auth.me as any)?.id || 0))

const canEditDraft = computed(() => {
  if (projectArchived.value) return false
  if (!release.value) return false
  if (String(release.value.status || '').toUpperCase() !== 'DRAFT') return false
  return myRole.value === 'OWNER' || myRole.value === 'DEVELOPER'
})

const canPublish = computed(() => canEditDraft.value && myRole.value === 'OWNER')
const canDelete = computed(() => {
  if (!canEditDraft.value) return false
  if (myRole.value === 'OWNER') return true
  if (myRole.value === 'DEVELOPER') return Number(release.value?.userId || 0) === myUserId.value
  return false
})

const isPublished = computed(() => String(release.value?.status || '').toUpperCase() === 'PUBLISHED')
const notesAssetId = computed(() => Number(release.value?.notesAssetId || 0))

const editVersion = ref('')
const editSummary = ref('')

const notesLoading = ref(false)
const notesMd = ref('')

const moreOptions = computed(() => {
  const opts: Array<{ key: string; label: string }> = [{ key: 'back', label: '返回项目' }]
  if (canEditDraft.value) opts.push({ key: 'gen', label: '生成发布说明' })
  if (canDelete.value) opts.push({ key: 'del', label: '删除记录' })
  return opts
})

function statusLabel(s?: string | null) {
  return String(s || '').toUpperCase() === 'PUBLISHED' ? '已发布' : '草稿'
}

function fmt(ts?: string | null) {
  if (!ts) return ''
  const d = new Date(ts)
  if (Number.isNaN(d.getTime())) return ts
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const dd = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${dd} ${hh}:${mm}`
}

async function loadMyRole() {
  try {
    const res = await projectCollabApi.members(projectId.value)
    myRole.value = res.myRole
  } catch {
    myRole.value = null
  }
}

async function loadMilestones() {
  try {
    milestones.value = await milestoneApi.list(projectId.value, true)
  } catch {
    milestones.value = []
  }
}

async function loadNotes() {
  notesMd.value = ''
  const assetId = notesAssetId.value
  if (!assetId) return
  notesLoading.value = true
  try {
    const file = await assetApi.preview(assetId)
    notesMd.value = await file.blob.text()
  } catch (e: any) {
    notesMd.value = ''
    message.error(e?.message || '加载发布说明失败')
  } finally {
    notesLoading.value = false
  }
}

async function load() {
  if (!Number.isFinite(projectId.value) || !Number.isFinite(releaseId.value)) return
  if (!ps.projects.length) await ps.load()
  loading.value = true
  try {
    release.value = await releaseApi.get(releaseId.value)
    editVersion.value = String(release.value?.version || '')
    editSummary.value = String(release.value?.summary || '')
    await Promise.all([loadMyRole(), loadMilestones()])
    await loadNotes()
  } catch (e: any) {
    message.error(e?.message || '加载失败')
    router.replace({ name: 'project-detail', params: { id: projectId.value } })
  } finally {
    loading.value = false
  }
}

async function saveDraft() {
  if (!release.value) return
  if (!canEditDraft.value) return
  const v = editVersion.value.trim()
  if (!v) {
    message.warning('请输入版本号')
    return
  }
  saving.value = true
  try {
    await releaseApi.updateDraft(release.value.id, {
      version: v,
      summary: editSummary.value.trim() ? editSummary.value.trim() : null
    })
    await load()
    message.success('已保存')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function generateNotes() {
  if (!release.value) return
  if (!canEditDraft.value) return
  saving.value = true
  try {
    await releaseApi.generateNotes(release.value.id)
    await load()
    message.success('已生成发布说明')
  } catch (e: any) {
    message.error(e?.message || '生成失败')
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!release.value) return
  if (!canPublish.value) return
  dialog.warning({
    title: '发布版本',
    content: `确认发布 ${release.value.version}？发布后将冻结内容，只读展示。`,
    positiveText: '发布',
    negativeText: '取消',
    onPositiveClick: async () => {
      saving.value = true
      try {
        await releaseApi.publish(release.value!.id)
        await load()
        message.success('已发布')
      } catch (e: any) {
        message.error(e?.message || '发布失败')
      } finally {
        saving.value = false
      }
    }
  })
}

async function remove() {
  if (!release.value) return
  if (!canDelete.value) return
  dialog.warning({
    title: '删除发版记录',
    content: `确认删除 ${release.value.version}？仅草稿可删除。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      saving.value = true
      try {
        await releaseApi.remove(release.value!.id)
        message.success('已删除')
        router.push({ name: 'project-detail', params: { id: projectId.value } })
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      } finally {
        saving.value = false
      }
    }
  })
}

function onMoreSelect(key: string) {
  if (key === 'back') {
    router.push({ name: 'project-detail', params: { id: projectId.value } })
    return
  }
  if (key === 'gen') {
    void generateNotes()
    return
  }
  if (key === 'del') {
    void remove()
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="page">
    <!-- 顶部操作栏 -->
    <div class="topbar">
      <div class="crumbs">
        <button class="crumb" type="button" @click="router.push({ name: 'project-detail', params: { id: projectId } })">
          项目
        </button>
        <span class="sep">/</span>
        <span class="crumbCur">发版记录</span>
      </div>
      <div class="topActions">
        <n-dropdown :options="moreOptions" placement="bottom-end" @select="onMoreSelect">
          <button class="btnGhost" type="button">更多</button>
        </n-dropdown>
        <button v-if="canPublish" class="btnPrimary" :disabled="saving" @click="publish">
          <span>发布</span>
          <span v-if="saving" class="spin" />
        </button>
      </div>
    </div>

    <!-- 加载态 -->
    <div v-if="loading || !release" class="loadingState">
      <div class="loadingText">正在加载发版记录…</div>
    </div>

    <template v-else>
      <!-- 版本标题区 -->
      <div class="releaseHero">
        <div class="versionRow">
          <h1 class="versionTitle">{{ release.version || '未命名版本' }}</h1>
          <span class="statusBadge" :class="{ published: isPublished }">{{ statusLabel(release.status) }}</span>
        </div>
        <div v-if="release.summary" class="heroSummary">
          <span class="summaryText">{{ release.summary }}</span>
        </div>
      </div>

      <!-- 主体双栏：左主内容 + 右侧边栏 -->
      <div class="grid">
        <!-- 左栏：发布说明（主内容） -->
        <section class="mainCard">
          <div class="cardHead">
            <div class="cardTitle">发布说明</div>
            <div v-if="notesAssetId" class="cardExtra">
              <a class="linkBtn" :href="`/api/assets/${notesAssetId}/preview`" target="_blank" rel="noreferrer">预览</a>
              <a class="linkBtn" :href="`/api/assets/${notesAssetId}/download`">下载</a>
            </div>
            <div v-else-if="!isPublished" class="cardExtra muted">尚未生成</div>
          </div>
          <div class="cardBody">
            <n-spin :show="notesLoading">
              <div v-if="notesMd" class="markdownWrap">
                <markdown-view :content="notesMd" />
              </div>
              <div v-else class="emptyState">
                <div class="emptyTitle">暂无发布说明</div>
                <div class="emptyDesc muted">
                  当前发版记录尚未生成发布说明。草稿状态下可点击下方按钮，由 AI 汇总关联里程碑的已完成任务。
                </div>
                <button v-if="canEditDraft" class="btnPrimary mt16" :disabled="saving" @click="generateNotes">
                  生成发布说明
                </button>
              </div>
            </n-spin>
          </div>
        </section>

        <!-- 右栏：侧边信息 -->
        <aside class="sideCol">
          <!-- 编辑区（仅草稿） -->
          <section v-if="canEditDraft" class="sideBlock">
            <div class="sideTitle">编辑信息</div>
            <div class="sideForm">
              <div class="sfRow">
                <div class="sfLabel">版本号</div>
                <n-input v-model:value="editVersion" size="small" placeholder="例如：v1.0.0" />
              </div>
              <div class="sfRow">
                <div class="sfLabel">变更摘要</div>
                <n-input
                  v-model:value="editSummary"
                  size="small"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 4 }"
                  placeholder="可选"
                />
              </div>
              <button class="btnPrimary btnFull" :disabled="saving || !editVersion.trim()" @click="saveDraft">
                <span>保存</span>
                <span v-if="saving" class="spin" />
              </button>
            </div>
          </section>

          <!-- 元信息 -->
          <section class="sideBlock">
            <div class="sideTitle">发布元信息</div>
            <div class="metaList">
              <div class="metaRow">
                <span class="metaKey">创建人</span>
                <span class="metaVal">用户 #{{ release.userId }}</span>
              </div>
              <div class="metaRow">
                <span class="metaKey">创建时间</span>
                <span class="metaVal mono">{{ fmt(release.createTime) }}</span>
              </div>
              <div class="metaRow">
                <span class="metaKey">关联里程碑</span>
                <span class="metaVal">{{ milestoneName || '—' }}</span>
              </div>
              <div v-if="release.publishedTime" class="metaRow">
                <span class="metaKey">发布时间</span>
                <span class="metaVal mono">{{ fmt(release.publishedTime) }}</span>
              </div>
            </div>
          </section>

          <!-- 操作（仅草稿） -->
          <section v-if="canEditDraft && !notesMd" class="sideBlock">
            <div class="sideTitle">操作</div>
            <button class="btnGhost btnFull" :disabled="saving" @click="generateNotes">
              <span>生成发布说明</span>
              <span v-if="saving" class="spin" />
            </button>
          </section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.page {
  padding: 0 0 40px;
  min-height: 100%;
}

/* ── 顶部栏 ────────────────────────────── */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
  flex-wrap: wrap;
}

.crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.crumb {
  border: 0;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--ink-3, #6b7280);
  cursor: pointer;
  user-select: none;
}
.crumb:hover {
  color: var(--brand);
}

.sep {
  color: rgba(15, 23, 42, 0.28);
}

.crumbCur {
  color: rgba(15, 23, 42, 0.9);
  font-weight: 700;
}

.topActions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

/* ── 按钮 ──────────────────────────────── */
.btnGhost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 14px;
  border-radius: 8px;
  background: #fff;
  color: rgba(15, 23, 42, 0.82);
  border: 1px solid var(--line, #e5e7eb);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 140ms ease, color 140ms ease;
}
.btnGhost:hover {
  border-color: var(--line-strong, #d1d5db);
  color: rgba(15, 23, 42, 0.95);
}
.btnGhost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btnPrimary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--brand);
  color: #fff;
  border: 1px solid var(--brand);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition: background 140ms ease;
}
.btnPrimary:hover {
  background: var(--brand-hover);
  border-color: var(--brand-hover);
}
.btnPrimary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btnFull {
  width: 100%;
}

.spin {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  animation: rel-spin 0.7s linear infinite;
  vertical-align: -1px;
}
@keyframes rel-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── 加载态 ────────────────────────────── */
.loadingState {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 420px;
}
.loadingText {
  font-size: 13px;
  color: var(--ink-3, #6b7280);
}

/* ── 版本标题区 ────────────────────────── */
.releaseHero {
  margin-bottom: 28px;
  padding-bottom: 24px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}

.versionRow {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.versionTitle {
  font-size: 28px;
  font-weight: 800;
  letter-spacing: -0.6px;
  line-height: 1.2;
  color: rgba(15, 23, 42, 0.92);
  margin: 0;
}

.statusBadge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  background: var(--surface-3, #f3f4f6);
  border: 1px solid var(--line, #e5e7eb);
  color: rgba(15, 23, 42, 0.72);
}
.statusBadge.published {
  background: var(--brand-soft, #eef2ff);
  border-color: rgba(30, 64, 175, 0.18);
  color: var(--brand);
}

.heroSummary {
  margin-top: 14px;
  font-size: 14px;
  line-height: 1.65;
  color: rgba(15, 23, 42, 0.78);
  max-width: 720px;
}
.summaryText {
  color: rgba(15, 23, 42, 0.78);
}

/* ── 主体双栏 ──────────────────────────── */
.grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 28px;
  align-items: start;
}

/* ── 主内容卡 ──────────────────────────── */
.mainCard {
  background: #fff;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.cardHead {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 22px;
  border-bottom: 1px solid var(--line, #e5e7eb);
}

.cardTitle {
  font-size: 14px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.92);
}

.cardExtra {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.linkBtn {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand);
  text-decoration: none;
  padding: 3px 8px;
  border-radius: 6px;
  border: 1px solid rgba(30, 64, 175, 0.15);
  background: var(--brand-soft, #eef2ff);
  transition: background 120ms ease;
}
.linkBtn:hover {
  background: rgba(30, 64, 175, 0.1);
}

.cardBody {
  flex: 1;
  min-height: 0;
  padding: 22px;
}

.markdownWrap {
  font-size: 14px;
  line-height: 1.75;
  color: rgba(15, 23, 42, 0.82);
}

/* 覆盖 Markdown 链接颜色 */
.markdownWrap :deep(a) {
  color: var(--brand);
  text-decoration: none;
}
.markdownWrap :deep(a:hover) {
  text-decoration: underline;
}
.markdownWrap :deep(h1),
.markdownWrap :deep(h2),
.markdownWrap :deep(h3) {
  color: rgba(15, 23, 42, 0.92);
  font-weight: 700;
  letter-spacing: -0.2px;
  margin-top: 24px;
  margin-bottom: 12px;
}
.markdownWrap :deep(p) {
  margin-bottom: 12px;
}
.markdownWrap :deep(ul),
.markdownWrap :deep(ol) {
  padding-left: 20px;
  margin-bottom: 12px;
}
.markdownWrap :deep(li) {
  margin-bottom: 6px;
}
.markdownWrap :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12.5px;
  padding: 2px 5px;
  border-radius: 5px;
  background: rgba(15, 23, 42, 0.04);
  color: rgba(15, 23, 42, 0.85);
}
.markdownWrap :deep(pre) {
  background: rgba(15, 23, 42, 0.03);
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 8px;
  padding: 14px;
  overflow: auto;
}
.markdownWrap :deep(pre code) {
  background: transparent;
  padding: 0;
}

/* ── 空态 ──────────────────────────────── */
.emptyState {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 320px;
  text-align: center;
  padding: 24px;
}
.emptyTitle {
  font-size: 15px;
  font-weight: 700;
  color: rgba(15, 23, 42, 0.85);
}
.emptyDesc {
  font-size: 13px;
  max-width: 420px;
  line-height: 1.7;
}
.mt16 {
  margin-top: 16px;
}

/* ── 侧边栏 ────────────────────────────── */
.sideCol {
  display: grid;
  gap: 20px;
}

.sideBlock {
  background: #fff;
  border: 1px solid var(--line, #e5e7eb);
  border-radius: 12px;
  padding: 18px 18px;
}

.sideTitle {
  font-size: 12px;
  font-weight: 800;
  color: rgba(15, 23, 42, 0.55);
  text-transform: uppercase;
  letter-spacing: 0.6px;
  margin-bottom: 14px;
}

.sideForm {
  display: grid;
  gap: 12px;
}

.sfRow {
  display: grid;
  gap: 6px;
}

.sfLabel {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink-3, #6b7280);
}

.metaList {
  display: grid;
  gap: 10px;
}

.metaRow {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  font-size: 12.5px;
}

.metaKey {
  color: var(--ink-3, #6b7280);
  flex-shrink: 0;
}

.metaVal {
  color: rgba(15, 23, 42, 0.88);
  text-align: right;
  word-break: break-word;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: 12px;
}

.muted {
  color: var(--ink-3, #6b7280);
}

/* ── 响应式 ────────────────────────────── */
@media (max-width: 980px) {
  .grid {
    grid-template-columns: 1fr;
  }
  .sideCol {
    order: 2;
  }
  .mainCard {
    order: 1;
    min-height: 360px;
  }
}
</style>
