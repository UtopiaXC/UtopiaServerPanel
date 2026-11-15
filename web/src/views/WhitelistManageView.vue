<template>
  <div class="whitelist-manage-page">
    <n-card :bordered="false" :title="$t('whitelist.manageTitle')">
      <n-tabs v-model:value="activeTab" type="line">
        <!-- Tab 1: Applications Queue -->
        <n-tab-pane name="applications" :tab="$t('whitelist.tabApplications')">
          <div style="display: flex; gap: 10px; margin-bottom: 12px; flex-wrap: wrap;">
            <n-select
              v-model:value="statusFilter"
              :options="statusOptions"
              style="width: 150px;"
              @update:value="loadApplications"
            />
            <n-input
              v-model:value="search"
              clearable
              :placeholder="$t('common.search')"
              style="width: 240px;"
              @keyup.enter="loadApplications"
            />
            <n-button secondary @click="loadApplications">{{ $t('common.refresh') }}</n-button>
          </div>

          <n-data-table
            size="small"
            :loading="loadingApps"
            :columns="appColumns"
            :data="applications"
            :pagination="false"
          />
        </n-tab-pane>

        <!-- Tab 2: Server Whitelist Entries -->
        <n-tab-pane name="entries" :tab="$t('whitelist.tabEntries')">
          <div style="display: flex; gap: 10px; margin-bottom: 12px; justify-content: space-between;">
            <n-input
              v-model:value="entrySearch"
              clearable
              :placeholder="$t('common.search')"
              style="width: 240px;"
            />
            <n-button type="primary" size="small" @click="showAddModal = true">
              {{ $t('whitelist.addEntryBtn') }}
            </n-button>
          </div>

          <n-data-table
            size="small"
            :loading="loadingEntries"
            :columns="entryColumns"
            :data="filteredEntries"
            :pagination="{ pageSize: 15 }"
          />
        </n-tab-pane>
      </n-tabs>
    </n-card>

    <!-- Review Note Modal -->
    <n-modal v-model:show="reviewModalOpen" preset="card" :title="$t('whitelist.reviewModalTitle')" style="max-width: 420px;">
      <n-form label-placement="top">
        <n-form-item :label="$t('whitelist.reviewNote')">
          <n-input v-model:value="reviewNote" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="reviewModalOpen = false">{{ $t('common.cancel') }}</n-button>
          <n-button
            :type="reviewApprove ? 'primary' : 'error'"
            :loading="reviewing"
            @click="submitReview"
          >
            {{ reviewApprove ? $t('whitelist.approveBtn') : $t('whitelist.rejectBtn') }}
          </n-button>
        </div>
      </template>
    </n-modal>

    <!-- Add Entry Modal -->
    <n-modal v-model:show="showAddModal" preset="card" :title="$t('whitelist.addEntryBtn')" style="max-width: 420px;">
      <n-form label-placement="top">
        <n-form-item :label="$t('whitelist.playerName')">
          <n-input v-model:value="newEntryName" />
        </n-form-item>
        <n-form-item label="UUID">
          <n-input v-model:value="newEntryUuid" :placeholder="$t('whitelist.uuidOptional')" />
        </n-form-item>
      </n-form>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showAddModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="addingEntry" @click="submitAddEntry">
            {{ $t('common.confirm') }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NSpace, NTag, useMessage, type DataTableColumns } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import {
  whitelistApi,
  type WhitelistApplicationItem,
  type WhitelistEntry
} from '../api/whitelist'
import { getApiErrorMessage } from '../api/client'

const message = useMessage()
const { t, te } = useI18n()

const activeTab = ref('applications')
const statusFilter = ref('pending')
const search = ref('')
const loadingApps = ref(false)
const applications = ref<WhitelistApplicationItem[]>([])

const loadingEntries = ref(false)
const entries = ref<WhitelistEntry[]>([])
const entrySearch = ref('')

const reviewModalOpen = ref(false)
const reviewTargetId = ref(0)
const reviewApprove = ref(true)
const reviewNote = ref('')
const reviewing = ref(false)

const showAddModal = ref(false)
const newEntryName = ref('')
const newEntryUuid = ref('')
const addingEntry = ref(false)

const statusOptions = computed(() => [
  { label: t('whitelist.statusPending'), value: 'pending' },
  { label: t('whitelist.statusApproved'), value: 'approved' },
  { label: t('whitelist.statusRejected'), value: 'rejected' },
  { label: t('common.all'), value: '' }
])

const filteredEntries = computed(() => {
  const q = entrySearch.value.trim().toLowerCase()
  if (!q) return entries.value
  return entries.value.filter(
    (e) => e.name.toLowerCase().includes(q) || (e.uuid || '').toLowerCase().includes(q)
  )
})

function formatSocialCell(raw?: string) {
  if (!raw || !raw.trim()) return '-'
  const trimmed = raw.trim()
  if (!trimmed.startsWith('[')) return trimmed
  try {
    const arr = JSON.parse(trimmed)
    if (!Array.isArray(arr) || arr.length === 0) return '-'
    return h(
      NSpace,
      { size: [4, 4], wrap: true },
      {
        default: () =>
          arr.map((item: any) => {
            const p = String(item?.platform || 'custom')
            const label =
              p.toLowerCase() === 'custom' && item?.customPlatform
                ? String(item.customPlatform)
                : te(`socialPlatforms.${p.toLowerCase()}`)
                  ? t(`socialPlatforms.${p.toLowerCase()}`)
                  : p
            return h(
              NTag,
              { size: 'tiny', type: 'info', bordered: false },
              { default: () => `${label}: ${item?.account || ''}` }
            )
          })
      }
    )
  } catch {
    return trimmed
  }
}

const appColumns = computed<DataTableColumns<WhitelistApplicationItem>>(() => [
  { title: t('whitelist.playerName'), key: 'username', width: 130 },
  {
    title: 'UUID',
    key: 'onlineUuid',
    width: 190,
    ellipsis: true,
    render: (row) => `${row.preferredUuidMode}: ${row.onlineUuid || row.offlineUuid || '-'}`
  },
  { title: t('whitelist.email'), key: 'email', width: 160, ellipsis: true },
  {
    title: t('whitelist.socialAccount'),
    key: 'socialAccount',
    width: 210,
    render: (row) => formatSocialCell(row.socialAccount)
  },
  { title: t('whitelist.reason'), key: 'reason', ellipsis: true },
  {
    title: t('common.status'),
    key: 'status',
    width: 95,
    render: (row) =>
      h(
        NTag,
        {
          size: 'small',
          type: row.status === 'approved' ? 'success' : row.status === 'rejected' ? 'error' : 'warning'
        },
        { default: () => row.status }
      )
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 150,
    render: (row) => {
      if (row.status !== 'pending') return h('span', { style: 'opacity:0.6' }, row.reviewNote || '-')
      return h(
        NSpace,
        { size: 'small' },
        {
          default: () => [
            h(
              NButton,
              {
                size: 'tiny',
                type: 'primary',
                onClick: () => openReview(row.id, true)
              },
              { default: () => t('whitelist.approveBtn') }
            ),
            h(
              NButton,
              {
                size: 'tiny',
                type: 'error',
                secondary: true,
                onClick: () => openReview(row.id, false)
              },
              { default: () => t('whitelist.rejectBtn') }
            )
          ]
        }
      )
    }
  }
])

const entryColumns = computed<DataTableColumns<WhitelistEntry>>(() => [
  { title: t('whitelist.playerName'), key: 'name', width: 200 },
  { title: 'UUID', key: 'uuid', ellipsis: true },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 100,
    render: (row) =>
      h(
        NButton,
        {
          size: 'tiny',
          type: 'error',
          secondary: true,
          onClick: () => removeEntry(row.name)
        },
        { default: () => t('common.delete') }
      )
  }
])

function openReview(id: number, approve: boolean) {
  reviewTargetId.value = id
  reviewApprove.value = approve
  reviewNote.value = ''
  reviewModalOpen.value = true
}

async function submitReview() {
  reviewing.value = true
  try {
    await whitelistApi.reviewApplication(reviewTargetId.value, {
      approve: reviewApprove.value,
      reviewNote: reviewNote.value.trim()
    })
    reviewModalOpen.value = false
    message.success(t('common.saved'))
    await Promise.all([loadApplications(), loadEntries()])
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    reviewing.value = false
  }
}

async function loadApplications() {
  loadingApps.value = true
  try {
    const res = await whitelistApi.listApplications({
      status: statusFilter.value || undefined,
      search: search.value.trim() || undefined
    })
    applications.value = res.items || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loadingApps.value = false
  }
}

async function loadEntries() {
  loadingEntries.value = true
  try {
    const res = await whitelistApi.listEntries()
    entries.value = res.items || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loadingEntries.value = false
  }
}

async function submitAddEntry() {
  if (!newEntryName.value.trim()) return
  addingEntry.value = true
  try {
    await whitelistApi.addEntry({
      username: newEntryName.value.trim(),
      uuid: newEntryUuid.value.trim() || undefined
    })
    newEntryName.value = ''
    newEntryUuid.value = ''
    showAddModal.value = false
    message.success(t('common.saved'))
    await loadEntries()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    addingEntry.value = false
  }
}

async function removeEntry(name: string) {
  try {
    await whitelistApi.removeEntry(name)
    message.success(t('common.deleted'))
    await loadEntries()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

onMounted(() => {
  loadApplications()
  loadEntries()
})
</script>
