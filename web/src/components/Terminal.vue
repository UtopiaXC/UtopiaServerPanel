<template>
  <div class="terminal-container">
    <div class="terminal-header">
      <h2>{{ $t('terminal.title') }}</h2>
      <div class="custom-dropdown" @click="filterDropdownOpen = !filterDropdownOpen" v-click-outside="() => filterDropdownOpen = false">
        <div class="custom-dropdown-btn">
          {{ logFilter === '' ? $t('terminal.allLevels') : logFilter }}
        </div>
        <ul class="custom-dropdown-menu" v-if="filterDropdownOpen">
          <li @click="setFilter('')" :class="{active: logFilter === ''}">{{ $t('terminal.allLevels') }}</li>
          <li @click="setFilter('INFO')" :class="{active: logFilter === 'INFO'}">INFO</li>
          <li @click="setFilter('WARN')" :class="{active: logFilter === 'WARN'}">WARN</li>
          <li @click="setFilter('ERROR')" :class="{active: logFilter === 'ERROR'}">ERROR</li>
          <li @click="setFilter('FATAL')" :class="{active: logFilter === 'FATAL'}">FATAL</li>
          <li @click="setFilter('DEBUG')" :class="{active: logFilter === 'DEBUG'}">DEBUG</li>
        </ul>
      </div>
    </div>
    <div class="terminal-output" ref="terminalOutput" @scroll="handleScroll">
      <div v-if="filteredLogs.length === 0" class="empty-console">
        {{ $t('terminal.noLogs') }}
      </div>
      <div
        v-for="(log, index) in filteredLogs"
        :key="index"
        class="log-line"
        :class="{ 'log-web': log.source === 'web' }"
      >
        <span class="log-time">{{ log.time }}</span>
        <span class="log-level" :class="'level-' + (log.level || 'INFO').toLowerCase()">[{{ log.level }}]</span>
        <span class="log-logger-container">
          <span class="log-logger">[{{ log.logger }}]</span>
          <div class="tooltip logger-tooltip">{{ log.logger }}</div>
        </span>
        <span class="log-msg" v-html="renderMessage(log)"></span>
      </div>
    </div>
    <div class="terminal-input-wrapper">
      <div class="completions" v-if="completions.length > 0">
        <span
          v-for="(comp, i) in completions"
          :key="i"
          :class="{ active: i === selectedCompletionIndex }"
          @mousedown.prevent="applyCompletion(comp)"
        >
          {{ comp }}
        </span>
      </div>
      <div class="terminal-input">
        <input
          type="text"
          v-model="commandInput"
          @input="handleInput"
          @keydown="handleKeydown"
          :placeholder="$t('terminal.placeholder')"
          ref="commandInputRef"
        />
        <button @click="executeCommand" :disabled="isExecuting">{{ $t('terminal.send') }}</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, onActivated, nextTick } from 'vue';
import { on, off, sendCommand, fetchCompletions, fetchLogs } from '../api.js';

const logs = ref([]);
const logFilter = ref('');
const filterDropdownOpen = ref(false);
const commandInput = ref('');
const terminalOutput = ref(null);
const commandInputRef = ref(null);
const isExecuting = ref(false);
const autoScroll = ref(true);

const setFilter = (lvl) => {
  logFilter.value = lvl;
  filterDropdownOpen.value = false;
};

// custom directive to close dropdown when clicking outside
const vClickOutside = {
  mounted(el, binding) {
    el.clickOutsideEvent = function(event) {
      if (!(el === event.target || el.contains(event.target))) {
        binding.value(event, el);
      }
    };
    document.addEventListener('click', el.clickOutsideEvent);
  },
  unmounted(el) {
    document.removeEventListener('click', el.clickOutsideEvent);
  }
};

const completions = ref([]);
const selectedCompletionIndex = ref(-1);
let currentReqId = 0;

const filteredLogs = computed(() => {
  if (!logFilter.value) return logs.value;
  return logs.value.filter(log => log.level && log.level.toUpperCase() === logFilter.value);
});

// ── Log handlers ──

const handleInitialLogs = (msg) => {
  logs.value = msg.data;
  scrollToBottom();
};

const handleNewLog = (msg) => {
  // msg.data is already a parsed object from api.js unwrapping
  const entry = msg.data;
  if (typeof entry === 'object') {
    logs.value.push(entry);
  } else if (typeof entry === 'string') {
    // Legacy fallback: treat as plain server message
    logs.value.push({
      time: '',
      level: 'INFO',
      logger: '',
      thread: '',
      message: entry,
      source: 'server'
    });
  }
  // Trim to max allowed (also handled server-side, safety net)
  if (logs.value.length > 2000) {
    logs.value.splice(0, logs.value.length - 2000);
  }
  scrollToBottom();
};

// ── ANSI / Spark rendering ──

const ANSI_COLORS = {
  '0': null,        // reset
  '30': '#1e1e1e',  // black
  '31': '#ef4444',  // red
  '32': '#22c55e',  // green
  '33': '#eab308',  // yellow
  '34': '#3b82f6',  // blue
  '35': '#a855f7',  // magenta
  '36': '#22d3ee',  // cyan
  '37': '#e5e5e5',  // white
  '90': '#6b7280',  // bright black (gray)
  '91': '#f87171',  // bright red
  '92': '#4ade80',  // bright green
  '93': '#facc15',  // bright yellow
  '94': '#60a5fa',  // bright blue
  '95': '#c084fc',  // bright magenta
  '96': '#67e8f9',  // bright cyan
  '97': '#ffffff',  // bright white
};

function parseAnsi(text) {
  if (!text) return '';
  const parts = [];
  let remaining = text;
  let currentStyles = '';

  const ansiRegex = /\x1b\[([0-9;]*)m/g;
  let match;
  let lastIndex = 0;

  while ((match = ansiRegex.exec(text)) !== null) {
    // Push text before this escape code
    if (match.index > lastIndex) {
      const segment = text.slice(lastIndex, match.index);
      if (segment) {
        parts.push(currentStyles ? `<span style="${currentStyles}">${escHtml(segment)}</span>` : escHtml(segment));
      }
    }

    // Process ANSI codes
    const codes = match[1].split(';').filter(c => c !== '');
    for (const code of codes) {
      if (code === '0' || code === '') {
        currentStyles = '';
      } else if (ANSI_COLORS[code]) {
        currentStyles += `color:${ANSI_COLORS[code]};`;
      } else if (code === '1') {
        currentStyles += 'font-weight:bold;';
      } else if (code === '4') {
        currentStyles += 'text-decoration:underline;';
      }
    }

    lastIndex = match.index + match[0].length;
  }

  // Remaining text
  if (lastIndex < text.length) {
    const segment = text.slice(lastIndex);
    if (segment) {
      parts.push(currentStyles ? `<span style="${currentStyles}">${escHtml(segment)}</span>` : escHtml(segment));
    }
  }

  return parts.join('');
}

// ── Message renderer ──

function renderMessage(log) {
  const msg = log.message;
  if (!msg) return '';

  // If message is already pre-formatted HTML (from command output), use directly
  if (log.messageFormat === 'html') {
    let html = msg;
    // Still highlight exceptions even in HTML content
    html = html.replace(/(\b[A-Z][a-z]+Exception\b)/g,
      '<span style="color:#f87171;font-weight:bold">$1</span>');
    return html;
  }

  // Plain text: parse ANSI and escape HTML
  let html = parseAnsi(msg);

  // Highlight web commands specially
  if (log.source === 'web') {
    html = `<span class="web-cmd-marker">></span> ${html}`;
  }

  // Highlight common Minecraft log patterns within message
  html = html.replace(/(\b[A-Z][a-z]+Exception\b)/g,
    '<span style="color:#f87171;font-weight:bold">$1</span>');
  html = html.replace(/(\bat\s+[\w.$]+\([\w.]+:\d+\))/g,
    '<span style="color:#6b7280">$1</span>');

  return html;
}

function escHtml(str) {
  return str
    .replace(/&/g, '&')
    .replace(/</g, '<')
    .replace(/>/g, '>');
}

// ── Scrolling ──

const scrollToBottom = () => {
  if (!autoScroll.value) return;
  nextTick(() => {
    if (terminalOutput.value) {
      terminalOutput.value.scrollTop = terminalOutput.value.scrollHeight;
    }
  });
};

const handleScroll = (e) => {
  const el = e.target;
  // Use a small threshold (10px) to account for fractional pixels during scroll
  autoScroll.value = el.scrollHeight - el.scrollTop - el.clientHeight <= 10;
};

// ── Command input ──

let completionTimeout = null;

const handleInput = () => {
  completions.value = [];
  selectedCompletionIndex.value = -1;

  if (completionTimeout) clearTimeout(completionTimeout);

  const cmd = commandInput.value;
  if (!cmd) return;

  completionTimeout = setTimeout(() => {
    currentReqId++;
    fetchCompletions(cmd, currentReqId);
  }, 150);
};

const handleCompletions = (msg) => {
  if (msg.requestId == currentReqId) {
    completions.value = msg.data;
  }
};

const handleKeydown = (e) => {
  if (e.key === 'Tab') {
    e.preventDefault();
    if (completions.value.length > 0) {
      const indexToApply = selectedCompletionIndex.value >= 0 ? selectedCompletionIndex.value : 0;
      applyCompletion(completions.value[indexToApply]);
    }
  } else if (e.key === 'ArrowUp') {
    e.preventDefault();
    if (completions.value.length > 0) {
      selectedCompletionIndex.value = selectedCompletionIndex.value <= 0
        ? completions.value.length - 1
        : selectedCompletionIndex.value - 1;
    }
  } else if (e.key === 'ArrowDown') {
    e.preventDefault();
    if (completions.value.length > 0) {
      selectedCompletionIndex.value = selectedCompletionIndex.value >= completions.value.length - 1
        ? 0
        : selectedCompletionIndex.value + 1;
    }
  } else if (e.key === 'Enter') {
    if (completions.value.length > 0 && selectedCompletionIndex.value >= 0) {
      e.preventDefault();
      applyCompletion(completions.value[selectedCompletionIndex.value]);
    } else {
      executeCommand();
    }
  }
};

const applyCompletion = (comp) => {
  const currentParts = commandInput.value.split(' ');
  currentParts.pop();
  if (currentParts.length > 0) {
    commandInput.value = currentParts.join(' ') + ' ' + comp + ' ';
  } else {
    commandInput.value = comp + ' ';
  }

  completions.value = [];
  selectedCompletionIndex.value = -1;
  commandInputRef.value?.focus();
};

const executeCommand = async () => {
  if (!commandInput.value.trim() || isExecuting.value) return;

  isExecuting.value = true;
  sendCommand(commandInput.value);

  commandInput.value = '';
  autoScroll.value = true;
  scrollToBottom();
  completions.value = [];
  selectedCompletionIndex.value = -1;

  setTimeout(() => {
    isExecuting.value = false;
  }, 100);
};

onMounted(() => {
  on('logs', handleInitialLogs);
  on('new_log', handleNewLog);
  on('completions', handleCompletions);
  fetchLogs();
});

onUnmounted(() => {
  off('logs', handleInitialLogs);
  off('new_log', handleNewLog);
  off('completions', handleCompletions);
  if (completionTimeout) clearTimeout(completionTimeout);
});

// When tab is re-activated (switching back from another tab), scroll to bottom
onActivated(() => {
  autoScroll.value = true;
  nextTick(() => {
    if (terminalOutput.value) {
      terminalOutput.value.scrollTop = terminalOutput.value.scrollHeight;
    }
  });
});

</script>

<style scoped>
.terminal-container {
  display: flex;
  flex-direction: column;
  height: 550px;
  background: #1e1e1e;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 6px rgba(0,0,0,0.3);
}

.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #2d2d30;
  border-bottom: 1px solid #000;
  padding: 10px 15px;
}

.terminal-container h2 {
  margin: 0;
  color: #fff;
  font-size: 1rem;
}


.terminal-output {
  flex-grow: 1;
  padding: 8px 12px;
  overflow-y: auto;
  color: #cccccc;
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 0.82rem;
  line-height: 1.55;
}

.empty-console {
  text-align: center;
  padding: 3rem 1rem;
  color: #6b7280;
  font-style: italic;
}

/* ── Log Line Structure ── */
.log-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  padding: 1px 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.log-line.log-web {
  border-left: 3px solid #22c55e;
  padding-left: 8px;
  margin-left: -12px;
  background: rgba(34, 197, 94, 0.06);
}

/* ── Log Parts ── */
.log-time {
  color: #6b7280;
  flex-shrink: 0;
  min-width: 56px;
}

.log-level {
  flex-shrink: 0;
  font-weight: 600;
  min-width: 48px;
}

.level-info    { color: #93c5fd; }
.level-warn    { color: #facc15; }
.level-error   { color: #f87171; }
.level-fatal   { color: #ef4444; background: rgba(239,68,68,0.15); padding: 0 3px; border-radius: 2px; }
.level-debug   { color: #9ca3af; }
.level-trace   { color: #6b7280; font-style: italic; }
.level-web     { color: #4ade80; font-weight: 700; }

.log-logger-container {
  position: relative;
  display: inline-block;
  flex-shrink: 0;
  max-width: 200px;
}

.log-logger {
  color: #9ca3af;
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
  cursor: default;
}

/* Tooltip Generic */
.tooltip {
  visibility: hidden;
  opacity: 0;
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  margin-bottom: 5px;
  background: #ffffff;
  color: #1e293b;
  border-radius: 4px;
  padding: 0.5rem 0.75rem;
  font-size: 0.8rem;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  border: 1px solid #e2e8f0;
  white-space: nowrap;
  transition: visibility 0.2s, opacity 0.2s;
  pointer-events: none;
}
.tooltip::after {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border-width: 5px;
  border-style: solid;
  border-color: #ffffff transparent transparent transparent;
  filter: drop-shadow(0 2px 2px rgba(0,0,0,0.05));
}
.log-logger-container:hover .tooltip {
  visibility: visible;
  opacity: 1;
}

.log-msg {
  color: #e5e5e5;
}

/* ── Web command marker ── */
.web-cmd-marker {
  color: #4ade80;
  font-weight: 700;
  margin-right: 2px;
}

/* ── Input Area ── */
.terminal-input-wrapper {
  position: relative;
  background: #252526;
  border-top: 1px solid #000;
}

.completions {
  position: absolute;
  bottom: 100%;
  left: 0;
  right: 0;
  background: #252526;
  border-top: 1px solid #444;
  display: flex;
  flex-wrap: wrap;
  padding: 5px;
  gap: 5px;
  max-height: 100px;
  overflow-y: auto;
}

.completions span {
  padding: 4px 8px;
  background: #333;
  color: #d4d4d4;
  border-radius: 4px;
  font-size: 0.85rem;
  font-family: 'Consolas', 'Courier New', monospace;
  cursor: pointer;
}

.completions span:hover,
.completions span.active {
  background: #064e3b;
  color: #fff;
}

.terminal-input {
  display: flex;
  padding: 10px 15px;
}

.terminal-input input {
  flex-grow: 1;
  background: #3c3c3c;
  border: 1px solid #555;
  color: #fff;
  padding: 10px 12px;
  border-radius: 4px;
  font-family: 'Consolas', 'Courier New', monospace;
  font-size: 1rem;
  outline: none;
}

.terminal-input input:focus {
  border-color: #007acc;
}

.terminal-input button {
  margin-left: 10px;
  background: #007acc;
  color: white;
  border: none;
  padding: 0 20px;
  border-radius: 4px;
  cursor: pointer;
  font-weight: bold;
  transition: background 0.2s;
}

.terminal-input button:hover {
  background: #005999;
}

.terminal-input button:disabled {
  background: #555;
  cursor: not-allowed;
}
</style>