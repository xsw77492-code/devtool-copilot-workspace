export const NOTIFICATION_TYPE_META: { type: string; label: string; group: 'mentions' | 'task' | 'project' | 'system' }[] = [
  { type: 'TASK_MENTION', label: '提及', group: 'mentions' },
  { type: 'TASK_REPLY', label: '回复', group: 'mentions' },
  { type: 'TASK_COMMENT', label: '评论', group: 'task' },
  { type: 'TASK_DUE_SOON', label: '到期提醒', group: 'task' },
  { type: 'TASK_FOLLOW_UPDATE', label: '关注更新', group: 'task' },

  { type: 'PROJECT_INVITE_RECEIVED', label: '收到邀请', group: 'project' },
  { type: 'PROJECT_INVITE_SENT', label: '邀请已发送', group: 'project' },
  { type: 'PROJECT_INVITE_ACCEPTED', label: '邀请被接受', group: 'project' },
  { type: 'PROJECT_INVITE_REJECTED', label: '邀请被拒绝', group: 'project' },
  { type: 'PROJECT_INVITE_REISSUED', label: '邀请链接已更新', group: 'project' },
  { type: 'PROJECT_INVITE_CANCELED', label: '邀请已取消', group: 'project' },
  { type: 'PROJECT_MEMBER_ROLE_CHANGED', label: '成员角色变更', group: 'project' },
  { type: 'PROJECT_MEMBER_REMOVED', label: '移出项目', group: 'project' },
  { type: 'PROJECT_MEMBER_DISABLED', label: '成员已禁用', group: 'project' },
  { type: 'PROJECT_MEMBER_ENABLED', label: '成员已启用', group: 'project' },
  { type: 'PROJECT_OWNER_TRANSFERRED_IN', label: '你已成为所有者', group: 'project' },
  { type: 'PROJECT_OWNER_TRANSFERRED_OUT', label: '你已转让所有权', group: 'project' },

  { type: 'SYSTEM', label: '系统通知', group: 'system' }
]

