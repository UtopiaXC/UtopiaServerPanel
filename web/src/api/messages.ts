import { api, unwrap } from './client'

export interface InSiteMessageItem {
  id: number
  msgType: 'notification' | 'private_mail' | 'player_mail' | string
  senderUserId: number | null
  senderName: string
  recipientUserId: number | null
  recipientPlayerName: string | null
  title: string
  content: string
  isRead: boolean
  deliveredInGame: boolean
  createdAt: number
}

export interface InGameChatItem {
  id: number
  channel: 'public' | 'whisper' | string
  source: 'mc' | 'web' | string
  senderUuid: string | null
  senderName: string
  recipientUuid: string | null
  recipientName: string | null
  content: string
  createdAt: number
}

export const messagesApi = {
  getInbox: (params: { type?: string; page?: number; pageSize?: number }) =>
    unwrap<{ items: InSiteMessageItem[]; total: number; unreadCount: number }>(
      api.get('/messages/inbox', { params })
    ),
  getSent: () => unwrap<{ items: InSiteMessageItem[] }>(api.get('/messages/sent')),
  getUnreadCount: () => unwrap<{ unreadCount: number }>(api.get('/messages/unread-count')),
  send: (payload: { target: string; title?: string; content: string }) =>
    unwrap<{ id: number; sent: boolean }>(api.post('/messages/send', payload)),
  markRead: (id: number) => unwrap<{ read: boolean }>(api.post(`/messages/${id}/read`)),
  markAllRead: () => unwrap<{ marked: boolean }>(api.post('/messages/read-all')),

  getChatHistory: (params: { channel?: 'public' | 'whisper'; page?: number; pageSize?: number }) =>
    unwrap<{
      items: InGameChatItem[]
      webChatSendEnabled: boolean
      boundPlayer?: { playerUuid: string; playerName: string; primary?: boolean } | null
      boundPlayers?: Array<{ playerUuid: string; playerName: string; primary?: boolean; boundAt?: number }>
    }>(api.get('/chat/history', { params })),
  sendChat: (content: string, playerUuid?: string) =>
    unwrap<{ sent: boolean }>(api.post('/chat/send', playerUuid ? { content, playerUuid } : { content }))
}
