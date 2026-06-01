import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { projectApi, type Project } from '../api/project'

export const useProjectStore = defineStore('project', () => {
  const projects = ref<Project[]>([])
  const loading = ref(false)
  const showArchived = ref(localStorage.getItem('dtc_show_archived_projects') === '1')

  const byId = computed(() => {
    const map = new Map<number, Project>()
    for (const p of projects.value) map.set(p.id, p)
    return map
  })

  const visibleProjects = computed(() => {
    if (showArchived.value) return projects.value
    return projects.value.filter((p) => Number((p as any).archived || 0) !== 1)
  })

  async function load() {
    loading.value = true
    try {
      projects.value = await projectApi.list(true)
    } finally {
      loading.value = false
    }
  }

  async function create(payload: { name: string; description: string }) {
    const projectId = await projectApi.create(payload)
    await load()
    return { id: projectId } as Project
  }

  function setShowArchived(v: boolean) {
    showArchived.value = !!v
    localStorage.setItem('dtc_show_archived_projects', showArchived.value ? '1' : '0')
  }

  return { projects, visibleProjects, showArchived, setShowArchived, loading, byId, load, create }
})
