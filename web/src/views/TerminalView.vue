<template>
  <n-card size="small" style="border-radius: 12px;">
    <template #header>
      <div class="term-header">
        <div style="display: flex; align-items: center; gap: 10px;">
          <span class="conn-dot" :class="{ online: connected, connecting }" />
          <span style="font-size: 15px; font-weight: 600;">{{ $t('terminal.title') }}</span>
          <n-tag size="tiny" :bordered="false" :type="connected ? 'success' : connecting ? 'warning' : 'error'">
            {{ connected ? $t('terminal.connected') : connecting ? $t('terminal.connecting') : $t('terminal.disconnected') }}
          </n-tag>
        </div>

        <div class="term-controls">
          <n-select
            v-model:value="levelFilter"
            :options="levelOptions"
            size="small"
            style="width: 112px;"
          />
          <n-button size="small" quaternary @click="clearLocal">
            <template #icon>
              <n-icon><TrashOutline /></n-icon>
            </template>
            {{ $t('terminal.clearView') }}
          </n-button>
          <n-button size="small" quaternary @click="handleRefresh">
            <template #icon>
              <n-icon><RefreshOutline /></n-icon>
            </template>
          </n-button>
        </div>
      </div>
    </template>

    <div ref="outputRef" class="console-output" @scroll="handleScroll">
      <div v-if="filteredLogs.length === 0" class="empty-console">
        {{ $t('common.noData') }}
      </div>

      <div
        v-for="(log, idx) in filteredLogs"
        :key="idx"
        class="log-line"
        :class="{ 'log-web': log.source === 'web' }"
      >
        <span class="log-time">{{ formatLocalTime(log) }}</span>
        <span class="log-level" :class="'level-' + (log.level || 'INFO').toLowerCase()">
          [{{ log.level || 'INFO' }}]
        </span>
        <span v-if="log.logger" class="log-logger" :title="log.logger">[{{ log.logger }}]</span>
        <span class="log-msg" v-html="renderMessage(log)"></span>
      </div>
    </div>

    <div v-if="canExecute" class="input-section">
      <div v-if="suggestions.length > 0" class="suggestions-bar">
        <span
          v-for="(comp, i) in suggestions"
          :key="i"
          class="suggestion-chip"
          :class="{ active: i === selectedIndex }"
          @mousedown.prevent="applyCompletion(comp)"
        >
          {{ comp }}
        </span>
      </div>

      <div class="command-row">
        <n-input
          ref="inputRef"
          v-model:value="commandInput"
          class="mono"
          :placeholder="$t('terminal.commandPlaceholder')"
          @input="handleInput"
          @keydown="handleKeydown"
        />
        <n-button
          type="primary"
          :disabled="!commandInput.trim() || !connected"
          @click="execute"
        >
          {{ $t('terminal.send') }}
        </n-button>
      </div>
    </div>

    <n-alert v-else type="info" :bordered="false" style="margin-top: 12px;">
      {{ $t('terminal.readonlyNotice') }}
    </n-alert>
  </n-card>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { TrashOutline, RefreshOutline } from '@vicons/ionicons5'
import { useWebSocket, type ConsoleLogEntry } from '../composables/useWebSocket'
import { useAuthStore } from '../stores/auth'

const { t } = useI18n()
const authStore = useAuthStore()
const {
  connected,
  connecting,
  logs,
  completions,
  commandHistory,
  ensureConnected,
  fetchLogs,
  sendCommand,
  requestCompletions,
  requestCommandHistory
} = useWebSocket()

function handleRefresh() {
  if (!connected.value) {
    ensureConnected()
  } else {
    fetchLogs()
    requestCommandHistory()
  }
}

const canExecute = computed(() => authStore.hasPermission('terminal', 2))

const levelFilter = ref<string>('ALL')
const clearedBeforeCount = ref(0)
const autoScroll = ref(true)
const outputRef = ref<HTMLElement | null>(null)
const inputRef = ref<any>(null)

const commandInput = ref('')
const suggestions = ref<string[]>([])
const selectedIndex = ref(-1)
const historyIndex = ref(-1)
let savedInput = ''
let currentReqId = 0
let debounceTimer: ReturnType<typeof setTimeout> | null = null

const levelOptions = computed(() => [
  { label: t('common.all'), value: 'ALL' },
  { label: 'INFO', value: 'INFO' },
  { label: 'WARN', value: 'WARN' },
  { label: 'ERROR', value: 'ERROR' },
  { label: 'FATAL', value: 'FATAL' },
  { label: 'DEBUG', value: 'DEBUG' }
])

const visibleLogs = computed(() => logs.value.slice(clearedBeforeCount.value))

const filteredLogs = computed(() => {
  if (levelFilter.value === 'ALL') return visibleLogs.value
  return visibleLogs.value.filter((l) => (l.level || 'INFO').toUpperCase() === levelFilter.value)
})

function clearLocal() {
  clearedBeforeCount.value = logs.value.length
}

function escHtml(str: string): string {
  return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const ANSI_COLORS: Record<string, string | null> = {
  '0': null,
  '30': '#64748b',
  '31': '#ef4444',
  '32': '#22c55e',
  '33': '#eab308',
  '34': '#3b82f6',
  '35': '#a855f7',
  '36': '#06b6d4',
  '37': '#cbd5e1',
  '90': '#94a3b8',
  '91': '#f87171',
  '92': '#4ade80',
  '93': '#facc15',
  '94': '#60a5fa',
  '95': '#c084fc',
  '96': '#22d3ee',
  '97': '#f8fafc'
}

function parseAnsi(text: string): string {
  if (!text) return ''
  const parts: string[] = []
  let currentStyles = ''
  const ansiRegex = /\x1b\[([0-9;]*)m/g
  let match: RegExpExecArray | null
  let lastIndex = 0

  while ((match = ansiRegex.exec(text)) !== null) {
    if (match.index > lastIndex) {
      const seg = escHtml(text.slice(lastIndex, match.index))
      parts.push(currentStyles ? `<span style="${currentStyles}">${seg}</span>` : seg)
    }
    const codes = match[1].split(';').filter(Boolean)
    if (codes.length === 0) currentStyles = ''
    for (const c of codes) {
      if (c === '0') currentStyles = ''
      else if (ANSI_COLORS[c]) currentStyles += `color:${ANSI_COLORS[c]};`
      else if (c === '1') currentStyles += 'font-weight:bold;'
      else if (c === '4') currentStyles += 'text-decoration:underline;'
    }
    lastIndex = match.index + match[0].length
  }
  if (lastIndex < text.length) {
    const seg = escHtml(text.slice(lastIndex))
    parts.push(currentStyles ? `<span style="${currentStyles}">${seg}</span>` : seg)
  }
  return parts.join('')
}

function renderMessage(log: ConsoleLogEntry): string {
  const msg = log.message
  if (!msg) return ''
  if (log.messageFormat === 'html') {
    return msg.replace(
      /(\b[A-Z][a-z]+Exception\b)/g,
      '<span style="color:#f87171;font-weight:bold">$1</span>'
    )
  }
  let html = parseAnsi(msg)
  if (log.source === 'web' && !html.startsWith('&gt; ')) {
    html = `<span style="color:var(--usp-primary);font-weight:700;">&gt;</span> ${html}`
  }
  html = html.replace(
    /(\b[A-Z][a-z]+Exception\b)/g,
    '<span style="color:#f87171;font-weight:bold">$1</span>'
  )
  return html
}

function formatLocalTime(log: ConsoleLogEntry): string {
  if (log.timestamp) {
    return new Date(log.timestamp).toLocaleTimeString([], { hour12: false })
  }
  return log.time || ''
}

function scrollToBottom() {
  if (!autoScroll.value) return
  nextTick(() => {
    if (outputRef.value) {
      outputRef.value.scrollTop = outputRef.value.scrollHeight
    }
  })
}

function handleScroll(e: Event) {
  const el = e.target as HTMLElement
  autoScroll.value = el.scrollHeight - el.scrollTop - el.clientHeight <= 14
}

function handleInput(val: string) {
  suggestions.value = []
  selectedIndex.value = -1
  if (debounceTimer) clearTimeout(debounceTimer)
  if (!val) return
  debounceTimer = setTimeout(() => {
    currentReqId++
    requestCompletions(val, String(currentReqId))
  }, 150)
}

function applyCompletion(comp: string) {
  const parts = commandInput.value.split(' ')
  parts.pop()
  commandInput.value = parts.length > 0 ? `${parts.join(' ')} ${comp} ` : `${comp} `
  suggestions.value = []
  selectedIndex.value = -1
  inputRef.value?.focus()
}

function execute() {
  const cmd = commandInput.value.trim()
  if (!cmd) return
  sendCommand(cmd)
  if (commandHistory.value[commandHistory.value.length - 1] !== cmd) {
    commandHistory.value.push(cmd)
  }
  historyIndex.value = -1
  savedInput = ''
  commandInput.value = ''
  suggestions.value = []
  selectedIndex.value = -1
  autoScroll.value = true
  scrollToBottom()
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Tab') {
    e.preventDefault()
    if (suggestions.value.length > 0) {
      const idx = selectedIndex.value >= 0 ? selectedIndex.value : 0
      applyCompletion(suggestions.value[idx])
    }
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    if (suggestions.value.length > 0) {
      selectedIndex.value =
        selectedIndex.value <= 0 ? suggestions.value.length - 1 : selectedIndex.value - 1
    } else if (commandHistory.value.length > 0) {
      if (historyIndex.value === -1) {
        savedInput = commandInput.value
        historyIndex.value = commandHistory.value.length - 1
      } else if (historyIndex.value > 0) {
        historyIndex.value--
      }
      commandInput.value = commandHistory.value[historyIndex.value]
    }
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    if (suggestions.value.length > 0) {
      selectedIndex.value =
        selectedIndex.value >= suggestions.value.length - 1 ? 0 : selectedIndex.value + 1
    } else if (historyIndex.value >= 0) {
      if (historyIndex.value < commandHistory.value.length - 1) {
        historyIndex.value++
        commandInput.value = commandHistory.value[historyIndex.value]
      } else {
        historyIndex.value = -1
        commandInput.value = savedInput
        savedInput = ''
      }
    }
  } else if (e.key === 'Enter') {
    if (suggestions.value.length > 0 && selectedIndex.value >= 0) {
      e.preventDefault()
      applyCompletion(suggestions.value[selectedIndex.value])
    } else {
      execute()
    }
  } else if (historyIndex.value >= 0) {
    historyIndex.value = -1
    savedInput = ''
  }
}

watch(
  () => filteredLogs.value.length,
  () => {
    scrollToBottom()
  }
)

watch(completions, (next) => {
  if (next.requestId === String(currentReqId)) {
    suggestions.value = next.items
  }
})

watch(
  connected,
  (isOpen) => {
    if (isOpen) {
      fetchLogs()
      requestCommandHistory()
    }
  },
  { immediate: true }
)

onMounted(() => {
  scrollToBottom()
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped>
.term-header {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.term-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.conn-dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #ef4444;
  display: inline-block;
}

.conn-dot.connecting {
  background: #f59e0b;
}

.conn-dot.online {
  background: var(--usp-primary);
}

.console-output {
  height: 520px;
  overflow-y: auto;
  padding: 12px 14px;
  border-radius: 8px;
  background: var(--usp-terminal-bg);
  color: var(--usp-terminal-text);
  border: 1px solid var(--usp-terminal-border);
  font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
  font-size: 12.5px;
  line-height: 1.55;
}

.empty-console {
  text-align: center;
  padding: 48px 0;
  color: var(--usp-text-muted);
}

.log-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
  padding: 1px 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.log-line.log-web {
  border-left: 3px solid var(--usp-primary);
  padding-left: 8px;
  margin-left: -10px;
  background: var(--usp-primary-soft);
}

.log-time {
  color: var(--usp-text-muted);
  flex-shrink: 0;
}

.log-level {
  font-weight: 600;
  flex-shrink: 0;
}

.level-info { color: #3b82f6; }
.level-warn { color: #eab308; }
.level-error { color: #ef4444; }
.level-fatal { color: #ef4444; font-weight: 700; }
.level-debug { color: #94a3b8; }
.level-web { color: var(--usp-primary); }

.log-logger {
  color: var(--usp-text-muted);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.input-section {
  position: relative;
  margin-top: 12px;
}

.suggestions-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px;
  margin-bottom: 8px;
  border-radius: 8px;
  background: var(--usp-surface-subtle);
  border: 1px solid var(--usp-border);
  max-height: 110px;
  overflow-y: auto;
}

.suggestion-chip {
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
  font-family: 'JetBrains Mono', Consolas, monospace;
  background: var(--usp-surface);
  border: 1px solid var(--usp-border);
  cursor: pointer;
}

.suggestion-chip.active,
.suggestion-chip:hover {
  border-color: var(--usp-primary);
  background: var(--usp-primary-soft);
  color: var(--usp-primary);
}

.command-row {
  display: flex;
  gap: 8px;
}
</style>

