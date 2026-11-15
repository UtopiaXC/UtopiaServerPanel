<template>
  <div class="messages-page">
    <n-card :bordered="false" :title="$t('messages.title')">
      <template #header-extra>
        <div style="display: flex; gap: 8px;">
          <n-button size="small" secondary @click="markAllRead">
            {{ $t('messages.markAllRead') }}
          </n-button>
          <n-button size="small" type="primary" @click="openCompose('')">
            {{ $t('messages.composeBtn') }}
          </n-button>
        </div>
      </template>

      <n-tabs v-model:value="activeTab" type="line">
        <!-- Tab 1: Inbox -->
        <n-tab-pane name="inbox" :tab="$t('messages.tabInbox')">
          <div style="display: flex; gap: 10px; margin-bottom: 12px;">
            <n-select
              v-model:value="inboxType"
              :options="typeOptions"
              size="small"
              style="width: 180px;"
              @update:value="loadInbox"
            />
            <n-button size="small" secondary @click="loadInbox">{{ $t('common.refresh') }}</n-button>
          </div>

          <n-spin :show="loadingInbox">
            <div v-if="inboxItems.length === 0" class="empty-hint">
              {{ $t('common.noData') }}
            </div>
            <div v-else class="msg-list">
              <div
                v-for="item in inboxItems"
                :key="item.id"
                class="msg-card"
                :class="{ 'msg-unread': !item.isRead }"
              >
                <div class="msg-head">
                  <div class="msg-title-row">
                    <n-tag size="tiny" :type="item.isRead ? 'default' : 'info'">
                      {{ item.msgType }}
                    </n-tag>
                    <span class="msg-title">{{ item.title || item.senderName }}</span>
                  </div>
                  <span class="msg-time">{{ formatTime(item.createdAt) }}</span>
                </div>
                <div class="msg-from">{{ $t('messages.from') }}: {{ item.senderName }}</div>
                <div class="msg-body">{{ item.content }}</div>
                <div class="msg-actions">
                  <n-button v-if="!item.isRead" size="tiny" secondary @click="markRead(item.id)">
                    {{ $t('messages.markRead') }}
                  </n-button>
                  <n-button
                    v-if="item.senderName && item.senderName !== 'System'"
                    size="tiny"
                    text
                    type="primary"
                    @click="openCompose(item.senderName)"
                  >
                    {{ $t('messages.replyBtn') }}
                  </n-button>
                </div>
              </div>
            </div>
          </n-spin>
        </n-tab-pane>

        <!-- Tab 2: Sent -->
        <n-tab-pane name="sent" :tab="$t('messages.tabSent')">
          <div v-if="sentItems.length === 0" class="empty-hint">{{ $t('common.noData') }}</div>
          <div v-else class="msg-list">
            <div v-for="item in sentItems" :key="item.id" class="msg-card">
              <div class="msg-head">
                <span class="msg-title">{{ item.title || item.recipientPlayerName || 'Mail' }}</span>
                <span class="msg-time">{{ formatTime(item.createdAt) }}</span>
              </div>
              <div class="msg-body">{{ item.content }}</div>
            </div>
          </div>
        </n-tab-pane>

        <!-- Tab 3: In-Game Public Chat Room -->
        <n-tab-pane name="publicChat" :tab="$t('messages.tabPublicChat')">
          <div class="chat-box" ref="chatListRef">
            <div v-if="publicChat.length === 0" class="empty-hint">{{ $t('common.noData') }}</div>
            <div v-for="c in publicChat" :key="c.id" class="chat-line">
              <span class="chat-time">[{{ formatClock(c.createdAt) }}]</span>
              <n-tag v-if="c.source === 'web'" size="tiny" type="info">WEB</n-tag>
              <span class="chat-sender">{{ c.senderName }}:</span>
              <span class="chat-text">{{ c.content }}</span>
            </div>
          </div>

          <div
            v-if="webChatSendEnabled && boundPlayers.length > 0"
            class="chat-input-row"
          >
            <n-select
              v-if="boundPlayers.length > 1"
              v-model:value="selectedSenderUuid"
              :options="boundPlayerOptions"
              style="width: 170px; flex-shrink: 0;"
            />
            <n-tag
              v-else
              size="medium"
              type="info"
              :bordered="false"
              style="flex-shrink: 0; height: 34px; padding: 0 10px;"
            >
              {{ boundPlayers[0]?.playerName }}
            </n-tag>
            <n-input
              v-model:value="chatDraft"
              :placeholder="$t('messages.chatPlaceholder')"
              style="flex: 1;"
              @keyup.enter="sendPublicChat"
            />
            <n-button type="primary" :loading="sendingChat" @click="sendPublicChat">
              {{ $t('common.submit') }}
            </n-button>
          </div>
          <n-alert v-else-if="webChatSendEnabled" type="info" style="margin-top: 10px;">
            {{ $t('messages.chatBindRequired') }}
          </n-alert>
        </n-tab-pane>

        <!-- Tab 4: Whisper Logs -->
        <n-tab-pane v-if="isAdmin || authStore.user?.bound" name="whispers" :tab="$t('messages.tabWhispers')">
          <div class="chat-box">
            <div v-if="whisperLogs.length === 0" class="empty-hint">{{ $t('common.noData') }}</div>
            <div v-for="c in whisperLogs" :key="c.id" class="chat-line">
              <span class="chat-time">[{{ formatClock(c.createdAt) }}]</span>
              <span class="chat-sender">{{ c.senderName }} -> {{ c.recipientName || '?' }}:</span>
              <span class="chat-text">{{ c.content }}</span>
            </div>
          </div>
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- Compose Modal -->
    <n-modal v-model:show="composeOpen" preset="card" :title="$t('messages.composeBtn')" style="max-width: 460px;">
      <n-form label-placement="top">
        <n-form-item :label="$t('messages.recipientLabel')">
          <n-input v-model:value="composeForm.target" :placeholder="$t('messages.recipientPlaceholder')" />
        </n-form-item>
        <n-form-item :label="$t('messages.subjectLabel')">
          <n-input v-model:value="composeForm.title" />
        </n-form-item>
        <n-form-item :label="$t('messages.contentLabel')">
          <n-input v-model:value="composeForm.content" type="textarea" :autosize="{ minRows: 3, maxRows: 6 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="composeOpen = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="sendingMail" @click="submitMail">
            {{ $t('common.submit') }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { messagesApi, type InGameChatItem, type InSiteMessageItem } from '../api/messages'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'
import { useWebSocket } from '../composables/useWebSocket'

const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()
const ws = useWebSocket()

const isAdmin = computed(() => authStore.hasPermission('admin', 1))

const activeTab = ref('inbox')
const inboxType = ref('')
const loadingInbox = ref(false)
const inboxItems = ref<InSiteMessageItem[]>([])
const sentItems = ref<InSiteMessageItem[]>([])

const publicChat = ref<InGameChatItem[]>([])
const whisperLogs = ref<InGameChatItem[]>([])
const webChatSendEnabled = ref(siteStore.webChatSendEnabled)
const chatDraft = ref('')
const sendingChat = ref(false)
const chatListRef = ref<HTMLElement | null>(null)
const serverBoundPlayers = ref<{ playerUuid: string; playerName: string; primary?: boolean }[]>([])
const selectedSenderUuid = ref('')

const boundPlayers = computed(() => {
  if (serverBoundPlayers.value.length > 0) return serverBoundPlayers.value
  const list = authStore.user?.boundPlayers
  if (Array.isArray(list) && list.length > 0) return list
  if (authStore.user?.bound && authStore.user?.playerUuid && authStore.user?.playerName) {
    return [{ playerUuid: authStore.user.playerUuid, playerName: authStore.user.playerName, primary: true }]
  }
  return []
})

const boundPlayerOptions = computed(() =>
  boundPlayers.value.map((p) => ({
    label: p.primary ? `${p.playerName} (${t('settings.primaryPlayer')})` : p.playerName,
    value: p.playerUuid
  }))
)

watch(
  boundPlayers,
  (list) => {
    if (list.length === 0) {
      selectedSenderUuid.value = ''
      return
    }
    if (!selectedSenderUuid.value || !list.some((p) => p.playerUuid === selectedSenderUuid.value)) {
      const primary = list.find((p) => p.primary) || list[0]
      selectedSenderUuid.value = primary.playerUuid
    }
  },
  { immediate: true }
)

const composeOpen = ref(false)
const sendingMail = ref(false)
const composeForm = reactive({ target: '', title: '', content: '' })

let offChat: (() => void) | null = null
let offInbox: (() => void) | null = null

const typeOptions = computed(() => [
  { label: t('common.all'), value: '' },
  { label: t('messages.typeNotification'), value: 'notification' },
  { label: t('messages.typePrivate'), value: 'private_mail' },
  { label: t('messages.typePlayer'), value: 'player_mail' }
])

function formatTime(ts: number) {
  if (!ts) return ''
  return new Date(ts).toLocaleString()
}

function formatClock(ts: number) {
  if (!ts) return ''
  return new Date(ts).toLocaleTimeString()
}

async function loadInbox() {
  loadingInbox.value = true
  try {
    const res = await messagesApi.getInbox({ type: inboxType.value || undefined })
    inboxItems.value = res.items || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loadingInbox.value = false
  }
}

async function loadSent() {
  try {
    const res = await messagesApi.getSent()
    sentItems.value = res.items || []
  } catch {
    // ignore
  }
}

async function loadChat(channel: 'public' | 'whisper') {
  try {
    const res = await messagesApi.getChatHistory({ channel, page: 1, pageSize: 100 })
    webChatSendEnabled.value = res.webChatSendEnabled
    if (Array.isArray(res.boundPlayers)) {
      serverBoundPlayers.value = res.boundPlayers
    }
    if (channel === 'public') {
      publicChat.value = (res.items || []).slice().reverse()
      await nextTick()
      if (chatListRef.value) chatListRef.value.scrollTop = chatListRef.value.scrollHeight
    } else {
      whisperLogs.value = res.items || []
    }
  } catch {
    // ignore
  }
}

async function markRead(id: number) {
  try {
    await messagesApi.markRead(id)
    const found = inboxItems.value.find((i) => i.id === id)
    if (found) found.isRead = true
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function markAllRead() {
  try {
    await messagesApi.markAllRead()
    inboxItems.value.forEach((i) => {
      i.isRead = true
    })
    message.success(t('common.saved'))
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

function openCompose(target: string) {
  composeForm.target = target
  composeForm.title = ''
  composeForm.content = ''
  composeOpen.value = true
}

async function submitMail() {
  if (!composeForm.target.trim() || !composeForm.content.trim()) return
  sendingMail.value = true
  try {
    await messagesApi.send({
      target: composeForm.target.trim(),
      title: composeForm.title.trim(),
      content: composeForm.content.trim()
    })
    composeOpen.value = false
    message.success(t('common.saved'))
    await loadSent()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    sendingMail.value = false
  }
}

async function sendPublicChat() {
  const txt = chatDraft.value.trim()
  if (!txt) return
  sendingChat.value = true
  try {
    await messagesApi.sendChat(txt, selectedSenderUuid.value || undefined)
    chatDraft.value = ''
    await loadChat('public')
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    sendingChat.value = false
  }
}

watch(activeTab, (tab) => {
  if (tab === 'sent') loadSent()
  else if (tab === 'publicChat') loadChat('public')
  else if (tab === 'whispers') loadChat('whisper')
})

onMounted(() => {
  loadInbox()
  offChat = ws.onInGameChat((data: any) => {
    if (!data) return
    if (data.channel === 'whisper') {
      whisperLogs.value.unshift(data)
    } else {
      publicChat.value.push(data)
      nextTick(() => {
        if (chatListRef.value) chatListRef.value.scrollTop = chatListRef.value.scrollHeight
      })
    }
  })
  offInbox = ws.onInboxNotification(() => {
    loadInbox()
  })
})

onBeforeUnmount(() => {
  if (offChat) offChat()
  if (offInbox) offInbox()
})
</script>

<style scoped>
.msg-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.msg-card {
  padding: 12px 14px;
  border-radius: 8px;
  background: rgba(128, 128, 128, 0.08);
}

.msg-unread {
  border-left: 3px solid #3b82f6;
}

.msg-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.msg-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg-title {
  font-weight: 600;
}

.msg-time {
  font-size: 12px;
  opacity: 0.65;
}

.msg-from {
  font-size: 12px;
  opacity: 0.75;
  margin-top: 4px;
}

.msg-body {
  margin-top: 8px;
  white-space: pre-wrap;
  font-size: 13px;
}

.msg-actions {
  margin-top: 8px;
  display: flex;
  gap: 10px;
}

.chat-box {
  height: 380px;
  overflow-y: auto;
  padding: 12px;
  border-radius: 8px;
  background: rgba(128, 128, 128, 0.08);
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chat-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  font-size: 13px;
}

.chat-time {
  font-size: 11px;
  opacity: 0.6;
}

.chat-sender {
  font-weight: 600;
}

.chat-input-row {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.empty-hint {
  text-align: center;
  opacity: 0.65;
  padding: 24px 0;
}
</style>
