<template>
  <n-card size="small" :title="$t('logs.lifecycleCard')" style="border-radius: 12px;">
    <template #header-extra>
      <n-button size="tiny" quaternary :loading="loading" @click="loadPage(page)">
        <template #icon>
          <n-icon><RefreshOutline /></n-icon>
        </template>
      </n-button>
    </template>

    <n-spin :show="loading">
      <div v-if="events.length === 0 && !loading" style="padding: 24px 0;">
        <n-empty :description="$t('common.noData')" />
      </div>

      <div v-else class="table-responsive">
        <n-table :bordered="false" :single-line="false" size="small">
          <thead>
            <tr>
              <th>{{ $t('logs.time') }}</th>
              <th>{{ $t('logs.event') }}</th>
              <th>{{ $t('logs.detail') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="evt in events" :key="evt.id">
              <td class="mono" style="white-space: nowrap; font-size: 12px;">
                {{ formatTimestamp(evt.ts) }}
              </td>
              <td>
                <n-tag size="small" :type="eventTagType(evt.eventType)" :bordered="false">
                  {{ eventLabel(evt.eventType) }}
                </n-tag>
              </td>
              <td style="font-size: 13px; color: var(--usp-text-muted);">
                {{ evt.detail || '-' }}
              </td>
            </tr>
          </tbody>
        </n-table>
      </div>

      <div v-if="total > pageSize" style="display: flex; justify-content: flex-end; margin-top: 12px;">
        <n-pagination
          v-model:page="page"
          :page-count="totalPages"
          size="small"
          @update:page="loadPage"
        />
      </div>
    </n-spin>
  </n-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { RefreshOutline } from '@vicons/ionicons5'
import { monitorApi } from '../api/monitor'
import { formatTimestamp } from '../utils/format'

const { t } = useI18n()

const loading = ref(false)
const events = ref<any[]>([])
const page = ref(1)
const total = ref(0)
const pageSize = 10

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

async function loadPage(p: number) {
  loading.value = true
  page.value = p
  try {
    const res = await monitorApi.queryLifecyclePaged(p, pageSize)
    events.value = res.events || []
    total.value = res.total || 0
  } catch {
    events.value = []
  } finally {
    loading.value = false
  }
}

function eventLabel(type: number | string) {
  switch (Number(type)) {
    case 0:
      return t('logs.lifecycleStart')
    case 1:
      return t('logs.lifecycleNormalStop')
    case 2:
      return t('logs.lifecycleAbnormalStop')
    case 3:
      return t('logs.lifecycleCrash')
    default:
      return '-'
  }
}

function eventTagType(type: number | string): 'success' | 'info' | 'warning' | 'error' {
  switch (Number(type)) {
    case 0:
      return 'success'
    case 1:
      return 'info'
    case 2:
      return 'warning'
    case 3:
      return 'error'
    default:
      return 'info'
  }
}

onMounted(() => {
  loadPage(1)
})
</script>

