<template>
  <div class="lifecycle-card">
    <div class="card-header">
      <div class="card-title">{{ $t('logs.serverLifecycle') }}</div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="spinner"></div>
    </div>

    <div v-else-if="events.length === 0" class="empty-state">
      <p>{{ $t('logs.noLifecycleData') }}</p>
    </div>

    <template v-else>
      <div class="table-wrapper">
        <table class="lifecycle-table">
          <thead>
            <tr>
              <th>{{ $t('logs.eventTime') }}</th>
              <th>{{ $t('logs.eventType') }}</th>
              <th>{{ $t('logs.eventDetail') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="evt in events" :key="evt.id">
              <td class="time-cell">{{ formatTime(evt.ts) }}</td>
              <td>
                <span class="event-badge" :class="eventClass(evt.eventType)">
                  {{ eventLabel(evt.eventType) }}
                </span>
              </td>
              <td class="detail-cell">{{ evt.detail || '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="pagination">
        <button class="page-btn" :disabled="page <= 1" @click="loadPage(page - 1)">
          {{ $t('logs.prevPage') }}
        </button>
        <span class="page-info">{{ page }} / {{ totalPages }}</span>
        <button class="page-btn" :disabled="page >= totalPages" @click="loadPage(page + 1)">
          {{ $t('logs.nextPage') }}
        </button>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { monitorAPI } from '../api/admin';

const { t } = useI18n();

const loading = ref(false);
const events = ref([]);
const page = ref(1);
const total = ref(0);
const pageSize = 10;

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)));

const loadPage = async (p) => {
  loading.value = true;
  page.value = p;
  try {
    const res = await monitorAPI.queryLifecyclePaged(p, pageSize);
    events.value = res.data.data.events || [];
    total.value = res.data.data.total || 0;
  } catch (e) {
    console.error('Failed to load lifecycle events', e);
  } finally {
    loading.value = false;
  }
};

const formatTime = (ts) => {
  const d = new Date(ts * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
};

const eventLabel = (type) => {
  const n = Number(type);
  switch (n) {
    case 0: return t('logs.serverStart');
    case 1: return t('logs.serverNormalStop');
    case 2: return t('logs.serverAbnormalStop');
    case 3: return t('logs.serverCrash');
    default: return '?';
  }
};

const eventClass = (type) => {
  const n = Number(type);
  switch (n) {
    case 0: return 'badge-start';
    case 1: return 'badge-normal-stop';
    case 2: return 'badge-abnormal-stop';
    case 3: return 'badge-crash';
    default: return '';
  }
};

onMounted(() => {
  loadPage(1);
});
</script>

<style scoped>
.lifecycle-card {
  background: var(--card-bg, #fff);
  border-radius: 8px;
  padding: 1rem;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1);
  border: 1px solid var(--border-color, #e5e7eb);
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 1rem;
}

.card-title {
  font-weight: 600;
  font-size: 1.1rem;
  color: var(--text-primary, #111827);
}

.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem;
}

.spinner {
  width: 30px;
  height: 30px;
  border: 3px solid var(--border-color, #e5e7eb);
  border-top-color: var(--primary-color, #3b82f6);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-state {
  text-align: center;
  padding: 2rem;
  color: var(--text-secondary, #6b7280);
}

.table-wrapper {
  overflow-x: auto;
}

.lifecycle-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.9rem;
}

.lifecycle-table th,
.lifecycle-table td {
  padding: 0.6rem 0.8rem;
  text-align: left;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.lifecycle-table th {
  font-weight: 600;
  color: var(--text-secondary, #6b7280);
  font-size: 0.8rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.lifecycle-table tbody tr:hover {
  background: var(--bg-color, #f9fafb);
}

.time-cell {
  white-space: nowrap;
  color: var(--text-primary, #111827);
  font-family: 'Courier New', monospace;
  font-size: 0.85rem;
}

.detail-cell {
  color: var(--text-secondary, #6b7280);
  font-size: 0.85rem;
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.event-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.8rem;
  font-weight: 500;
  white-space: nowrap;
}

.badge-start {
  background: #dcfce7;
  color: #166534;
}

.badge-normal-stop {
  background: #dbeafe;
  color: #1e40af;
}

.badge-abnormal-stop {
  background: #fef3c7;
  color: #92400e;
}

.badge-crash {
  background: #fee2e2;
  color: #991b1b;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 1rem;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-color, #e5e7eb);
}

.page-btn {
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 4px;
  padding: 4px 14px;
  font-size: 0.85rem;
  cursor: pointer;
  color: var(--text-secondary, #6b7280);
  transition: all 0.2s;
}

.page-btn:hover:not(:disabled) {
  background: var(--bg-color, #f3f4f6);
  color: var(--primary-color, #3b82f6);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  font-size: 0.85rem;
  color: var(--text-secondary, #6b7280);
}

/* Dark mode support */
:root[data-theme="dark"] .badge-start {
  background: #052e16;
  color: #86efac;
}
:root[data-theme="dark"] .badge-normal-stop {
  background: #172554;
  color: #93c5fd;
}
:root[data-theme="dark"] .badge-abnormal-stop {
  background: #451a03;
  color: #fcd34d;
}
:root[data-theme="dark"] .badge-crash {
  background: #450a0a;
  color: #fca5a5;
}
</style>
