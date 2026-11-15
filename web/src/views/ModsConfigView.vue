<template>
  <div class="mods-page">
    <n-alert
      v-if="overview?.pendingRestart"
      type="warning"
      :title="$t('mods.pendingRestartTitle')"
      style="margin-bottom: 14px;"
    >
      <div class="restart-banner">
        <span>{{ overview.restartHint || $t('mods.pendingRestartDesc') }}</span>
        <n-button size="small" type="warning" @click="confirmRestart">
          {{ $t('mods.restartServer') }}
        </n-button>
      </div>
    </n-alert>

    <n-spin :show="loading">
      <n-card :bordered="false">
        <template #header>{{ $t('mods.title') }}</template>
        <template #header-extra>
          <div style="display: flex; gap: 8px;">
            <n-button size="small" secondary @click="loadOverview">{{ $t('common.refresh') }}</n-button>
            <n-button size="small" type="warning" secondary @click="confirmRestart">
              {{ $t('mods.restartServer') }}
            </n-button>
          </div>
        </template>

        <n-tabs v-model:value="activeTab" type="line">
          <!-- Tab 1: Installed Mods -->
          <n-tab-pane name="mods" :tab="$t('mods.tabMods')">
            <div style="margin-bottom: 12px; max-width: 340px;">
              <n-input v-model:value="modSearch" clearable :placeholder="$t('mods.searchMods')" />
            </div>
            <n-data-table
              size="small"
              :columns="modColumns"
              :data="filteredMods"
              :pagination="{ pageSize: 15 }"
            />
          </n-tab-pane>

          <!-- Tab 2: Config Files -->
          <n-tab-pane name="configs" :tab="$t('mods.tabConfigs')">
            <div style="margin-bottom: 12px; display: flex; gap: 10px; align-items: center;">
              <n-input
                v-model:value="cfgSearch"
                clearable
                :placeholder="$t('mods.searchConfigs')"
                style="max-width: 340px;"
              />
              <n-switch v-model:value="onlyUnmapped">
                <template #checked>{{ $t('mods.onlyUnmapped') }}</template>
                <template #unchecked>{{ $t('mods.allConfigs') }}</template>
              </n-switch>
            </div>
            <div class="config-file-grid">
              <div
                v-for="path in filteredConfigPaths"
                :key="path"
                class="config-file-card"
                @click="openConfigEditor(path)"
              >
                <span class="config-file-path">{{ path }}</span>
                <n-button size="tiny" secondary>{{ $t('common.edit') }}</n-button>
              </div>
            </div>
          </n-tab-pane>

          <!-- Tab 3: server.properties -->
          <n-tab-pane name="serverProperties" tab="server.properties">
            <div v-if="spLoaded">
              <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; flex-wrap: wrap; gap: 8px;">
                <n-radio-group v-model:value="spMode" size="small">
                  <n-radio-button value="kv">{{ $t('mods.structuredMode') }}</n-radio-button>
                  <n-radio-button value="raw">{{ $t('mods.rawMode') }}</n-radio-button>
                </n-radio-group>
                <n-input
                  v-if="spMode === 'kv'"
                  v-model:value="spSearch"
                  size="small"
                  clearable
                  :placeholder="$t('mods.searchKey')"
                  style="width: 240px;"
                />
                <n-button type="primary" size="small" :loading="savingSp" @click="saveServerProperties">
                  {{ $t('common.save') }}
                </n-button>
              </div>

              <n-data-table
                v-if="spMode === 'kv'"
                size="small"
                :columns="spColumns"
                :data="filteredSpEntries"
                :pagination="{ pageSize: 20 }"
              />
              <n-input
                v-else
                v-model:value="spRaw"
                type="textarea"
                :autosize="{ minRows: 16, maxRows: 28 }"
                style="font-family: monospace;"
              />
            </div>
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </n-spin>

    <!-- Config Editor Drawer -->
    <n-drawer v-model:show="drawerOpen" :width="680" placement="right">
      <n-drawer-content :title="editingPath" closable>
        <n-spin :show="loadingCfg">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
            <n-radio-group v-model:value="cfgMode" size="small">
              <n-radio-button value="kv">{{ $t('mods.structuredMode') }}</n-radio-button>
              <n-radio-button value="raw">{{ $t('mods.rawMode') }}</n-radio-button>
            </n-radio-group>
            <n-button type="primary" size="small" :loading="savingCfg" @click="saveConfigDoc">
              {{ $t('common.save') }}
            </n-button>
          </div>

          <div v-if="cfgMode === 'kv'">
            <div v-if="cfgEntries.length === 0" class="empty-hint">
              {{ $t('mods.useRawHint') }}
            </div>
            <div v-else class="kv-list">
              <div v-for="(entry, idx) in cfgEntries" :key="`${entry.section || ''}:${entry.key}:${idx}`" class="kv-row">
                <div class="kv-key" :title="entry.key">
                  {{ entry.section ? `[${entry.section}] ${entry.key}` : entry.key }}
                </div>
                <n-input
                  size="small"
                  :value="cfgUpdates[entryKey(entry)] ?? entry.value"
                  @update:value="(v) => (cfgUpdates[entryKey(entry)] = v)"
                />
              </div>
            </div>
          </div>

          <n-input
            v-else
            v-model:value="cfgRaw"
            type="textarea"
            :autosize="{ minRows: 20, maxRows: 34 }"
            style="font-family: monospace;"
          />
        </n-spin>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { NButton, NInput, NSpace, NTag, useDialog, useMessage, type DataTableColumns } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { modsApi, type ConfigEntry, type ModEntry, type ModsOverview } from '../api/mods'
import { getApiErrorMessage } from '../api/client'

const message = useMessage()
const dialog = useDialog()
const { t } = useI18n()

const loading = ref(false)
const activeTab = ref('mods')
const overview = ref<ModsOverview | null>(null)
const modSearch = ref('')
const cfgSearch = ref('')
const onlyUnmapped = ref(false)

const drawerOpen = ref(false)
const loadingCfg = ref(false)
const savingCfg = ref(false)
const editingPath = ref('')
const cfgMode = ref<'kv' | 'raw'>('kv')
const cfgRaw = ref('')
const cfgEntries = ref<ConfigEntry[]>([])
const cfgUpdates = ref<Record<string, string>>({})

const spLoaded = ref(false)
const savingSp = ref(false)
const spMode = ref<'kv' | 'raw'>('kv')
const spSearch = ref('')
const spRaw = ref('')
const spEntries = ref<ConfigEntry[]>([])
const spUpdates = ref<Record<string, string>>({})

const filteredMods = computed(() => {
  const mods = overview.value?.mods || []
  const q = modSearch.value.trim().toLowerCase()
  if (!q) return mods
  return mods.filter(
    (m) =>
      m.name.toLowerCase().includes(q) ||
      m.modId.toLowerCase().includes(q) ||
      (m.authors || '').toLowerCase().includes(q)
  )
})

const filteredConfigPaths = computed(() => {
  const src = onlyUnmapped.value
    ? overview.value?.unmappedConfigFiles || []
    : overview.value?.allConfigFiles || []
  const q = cfgSearch.value.trim().toLowerCase()
  if (!q) return src
  return src.filter((p) => p.toLowerCase().includes(q))
})

const filteredSpEntries = computed(() => {
  const q = spSearch.value.trim().toLowerCase()
  if (!q) return spEntries.value
  return spEntries.value.filter((e) => e.key.toLowerCase().includes(q))
})

const modColumns = computed<DataTableColumns<ModEntry>>(() => [
  { title: t('mods.colName'), key: 'name', width: 200 },
  { title: t('mods.colModId'), key: 'modId', width: 160 },
  { title: t('mods.colVersion'), key: 'version', width: 120 },
  { title: t('mods.colAuthors'), key: 'authors', ellipsis: true },
  {
    title: t('mods.colConfigs'),
    key: 'configFiles',
    render: (row) => {
      if (!row.configFiles || row.configFiles.length === 0) {
        return h('span', { style: 'opacity: 0.5;' }, '-')
      }
      return h(
        NSpace,
        { size: 'small' },
        {
          default: () =>
            row.configFiles.map((cf) =>
              h(
                NTag,
                {
                  size: 'small',
                  type: 'info',
                  style: 'cursor: pointer;',
                  onClick: () => openConfigEditor(cf)
                },
                { default: () => cf.split('/').pop() || cf }
              )
            )
        }
      )
    }
  }
])

const spColumns = computed<DataTableColumns<ConfigEntry>>(() => [
  { title: t('mods.colKey'), key: 'key', width: 260 },
  {
    title: t('mods.colValue'),
    key: 'value',
    render: (row) =>
      h(NInput, {
        size: 'small',
        value: spUpdates.value[row.key] ?? row.value,
        onUpdateValue: (val: string) => {
          spUpdates.value[row.key] = val
        }
      })
  }
])

async function loadOverview() {
  loading.value = true
  try {
    overview.value = await modsApi.getOverview()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function entryKey(e: ConfigEntry) {
  return `${e.section || ''}\u0000${e.key}`
}

async function openConfigEditor(path: string) {
  editingPath.value = path
  drawerOpen.value = true
  loadingCfg.value = true
  cfgUpdates.value = {}
  try {
    const doc = await modsApi.getConfig(path)
    cfgRaw.value = doc.content || ''
    cfgEntries.value = doc.structuredEntries || []
    cfgMode.value = cfgEntries.value.length > 0 ? 'kv' : 'raw'
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loadingCfg.value = false
  }
}

async function saveConfigDoc() {
  savingCfg.value = true
  try {
    const updateList = Object.entries(cfgUpdates.value).map(([k, value]) => {
      const idx = k.indexOf('\u0000')
      const section = idx >= 0 ? k.slice(0, idx) : ''
      const key = idx >= 0 ? k.slice(idx + 1) : k
      return section ? { section, key, value } : { key, value }
    })
    const payload =
      cfgMode.value === 'raw'
        ? { path: editingPath.value, content: cfgRaw.value }
        : { path: editingPath.value, updates: updateList }
    const res = await modsApi.saveConfig(payload)
    if (overview.value) {
      overview.value.pendingRestart = res.pendingRestart
      if (res.restartHint) overview.value.restartHint = res.restartHint
    }
    message.success(t('common.saved'))
    drawerOpen.value = false
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingCfg.value = false
  }
}

async function loadServerProperties() {
  try {
    const res = await modsApi.getServerProperties()
    spRaw.value = res.raw
    spEntries.value = res.entries || []
    spUpdates.value = {}
    spLoaded.value = true
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function saveServerProperties() {
  savingSp.value = true
  try {
    const updateList = Object.entries(spUpdates.value).map(([key, value]) => ({ key, value }))
    const payload =
      spMode.value === 'raw' ? { raw: spRaw.value } : { updates: updateList }
    const res = await modsApi.saveServerProperties(payload)
    if (overview.value) {
      overview.value.pendingRestart = res.pendingRestart
      if (res.restartHint) overview.value.restartHint = res.restartHint
    }
    message.success(t('common.saved'))
    await loadServerProperties()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    savingSp.value = false
  }
}

function confirmRestart() {
  const hint = overview.value?.restartHint?.trim()
  const baseDesc = t('mods.restartConfirmDesc')
  const fullDesc = hint ? `${baseDesc}\n${hint}` : baseDesc
  dialog.warning({
    title: t('mods.restartConfirmTitle'),
    content: fullDesc,
    positiveText: t('common.confirm'),
    negativeText: t('common.cancel'),
    onPositiveClick: async () => {
      try {
        const res = await modsApi.restartServer()
        message.success(res.restartHint || t('mods.restartTriggered'))
      } catch (err) {
        message.error(getApiErrorMessage(err))
      }
    }
  })
}

watch(activeTab, (tab) => {
  if (tab === 'serverProperties' && !spLoaded.value) {
    loadServerProperties()
  }
})

onMounted(() => {
  loadOverview()
})
</script>

<style scoped>
.restart-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.config-file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}

.config-file-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  background: rgba(128, 128, 128, 0.08);
  cursor: pointer;
}

.config-file-card:hover {
  background: rgba(128, 128, 128, 0.14);
}

.config-file-path {
  font-family: monospace;
  font-size: 12px;
  word-break: break-all;
}

.kv-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.kv-row {
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 10px;
  align-items: center;
}

.kv-key {
  font-family: monospace;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.empty-hint {
  opacity: 0.7;
  padding: 20px 0;
  text-align: center;
}
</style>
