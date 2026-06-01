import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { taskApi, type Task, type TaskStatus } from '../api/task'

export const useTaskStore = defineStore('task', () => {
  const tasks = ref<Task[]>([])
  const loading = ref(false)

  const grouped = computed(() => {
    const todo: Task[] = []
    const doing: Task[] = []
    const done: Task[] = []
    for (const t of tasks.value) {
      if (t.status === 'DOING') doing.push(t)
      else if (t.status === 'DONE') done.push(t)
      else todo.push(t)
    }
    return { todo, doing, done }
  })

  async function loadByProject(projectId: number) {
    loading.value = true
    try {
      tasks.value = await taskApi.listByProject(projectId)
    } finally {
      loading.value = false
    }
  }

  async function create(
    projectId: number,
    title: string,
    opts?: {
      assigneeId?: number | null
      dueAt?: number | null
      priority?: string | null
      tags?: string | null
      description?: string | null
      acceptanceCriteria?: string | null
      milestoneId?: number | null
      parentTaskId?: number | null
      type?: string | null
      source?: string
    }
  ) {
    const taskId = await taskApi.create(projectId, title, opts)
    await loadByProject(projectId)
    return { id: taskId } as Task
  }

  async function updateStatus(taskId: number, status: TaskStatus, baseUpdatedAt?: string | null) {
    const ok = await taskApi.updateStatusSafe(taskId, status, baseUpdatedAt ?? null)
    if (!ok) return false
    tasks.value = tasks.value.map((t) => (t.id === taskId ? { ...t, status } : t))
    return true
  }

  return { tasks, loading, grouped, loadByProject, create, updateStatus }
})
