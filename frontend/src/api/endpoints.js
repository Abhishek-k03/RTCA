import { request } from './client'

const qs = (params) => {
  const s = new URLSearchParams(
    Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''),
  ).toString()
  return s ? `?${s}` : ''
}

export const api = {
  register: (body) => request('POST', '/api/auth/register', body),
  login: (login, password) => request('POST', '/api/auth/login', { login, password }),

  me: () => request('GET', '/api/users/me'),
  updateMe: (displayName) => request('PATCH', '/api/users/me', { displayName }),
  user: (id) => request('GET', `/api/users/${id}`),
  searchUsers: (q) => request('GET', `/api/users/search${qs({ q, size: 20 })}`),

  conversations: (page = 0) => request('GET', `/api/conversations${qs({ page, size: 50 })}`),
  conversation: (id) => request('GET', `/api/conversations/${id}`),
  deleteConversation: (id) => request('DELETE', `/api/conversations/${id}`),
  clearConversation: (id) => request('POST', `/api/conversations/${id}/clear`),
  createDirect: (userId) => request('POST', '/api/conversations/direct', { userId }),
  createGroup: (name, memberIds) => request('POST', '/api/conversations/groups', { name, memberIds }),
  renameGroup: (id, name) => request('PATCH', `/api/conversations/groups/${id}`, { name }),
  addMembers: (id, userIds) => request('POST', `/api/conversations/groups/${id}/members`, { userIds }),
  removeMember: (id, userId) => request('DELETE', `/api/conversations/groups/${id}/members/${userId}`),
  changeParticipantRole: (id, userId, role) =>
    request('PATCH', `/api/conversations/groups/${id}/members/${userId}/role`, { role }),

  messages: (id, { before, after, limit } = {}) =>
    request('GET', `/api/conversations/${id}/messages${qs({ before, after, limit })}`),
  sendMessage: (id, clientMessageId, content) =>
    request('POST', `/api/conversations/${id}/messages`, { clientMessageId, content }),
  editMessage: (id, messageId, content) =>
    request('PATCH', `/api/conversations/${id}/messages/${messageId}`, { content }),
  // scope: 'me' | 'everyone'
  deleteMessage: (id, messageId, scope) =>
    request('DELETE', `/api/conversations/${id}/messages/${messageId}${qs({ scope })}`),
  markRead: (id, messageId) => request('POST', `/api/conversations/${id}/messages/read`, { messageId }),

  presence: (userIds) => request('GET', `/api/presence${qs({ userIds: userIds.join(',') })}`),

  adminUsers: (page = 0) => request('GET', `/api/admin/users${qs({ page, size: 20 })}`),
  adminChangeRole: (id, role) => request('PATCH', `/api/admin/users/${id}/role`, { role }),
}
