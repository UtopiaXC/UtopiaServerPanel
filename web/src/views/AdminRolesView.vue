<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">{{ $t('adminRoles.title') }}</h2>
      <n-button v-if="canWrite" type="primary" @click="openCreate">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        {{ $t('adminRoles.create') }}
      </n-button>
    </div>

    <n-card size="small" style="border-radius: 12px;">
      <n-spin :show="loading">
        <div class="table-responsive">
          <n-table :bordered="false" :single-line="false">
            <thead>
              <tr>
                <th style="width: 64px;">ID</th>
                <th>{{ $t('adminRoles.name') }}</th>
                <th>{{ $t('adminRoles.displayName') }}</th>
                <th>{{ $t('adminRoles.permissions') }}</th>
                <th style="width: 80px;">{{ $t('adminRoles.userCount') }}</th>
                <th v-if="canWrite" style="text-align: right;">{{ $t('common.actions') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in roles" :key="r.id">
                <td>{{ r.id }}</td>
                <td>
                  <code class="mono">{{ r.name }}</code>
                  <n-tag
                    v-if="r.systemRole"
                    size="tiny"
                    type="info"
                    :bordered="false"
                    style="margin-left: 6px;"
                  >
                    {{ $t('adminRoles.systemTag') }}
                  </n-tag>
                  <n-tag
                    v-if="r.immutable"
                    size="tiny"
                    type="warning"
                    :bordered="false"
                    style="margin-left: 4px;"
                  >
                    {{ $t('adminRoles.immutableTag') }}
                  </n-tag>
                </td>
                <td>
                  <strong>{{ r.displayName }}</strong>
                  <div v-if="r.description" style="font-size: 12px; color: var(--usp-text-muted);">
                    {{ r.description }}
                  </div>
                </td>
                <td>
                  <div style="display: flex; flex-wrap: wrap; gap: 6px;">
                    <n-tag
                      v-for="p in permDefs"
                      :key="permKeyOf(p)"
                      size="small"
                      :bordered="false"
                      :type="levelTagType(r.permissionLevels?.[permKeyOf(p)] ?? 0)"
                    >
                      {{ permTitle(p) }}: {{ levelLabel(r.permissionLevels?.[permKeyOf(p)] ?? 0) }}
                    </n-tag>
                  </div>
                </td>
                <td>{{ r.userCount }}</td>
                <td v-if="canWrite" style="text-align: right; white-space: nowrap;">
                  <div style="display: inline-flex; gap: 6px;">
                    <n-button size="tiny" quaternary type="primary" @click="openEdit(r)">
                      {{ $t('common.edit') }}
                    </n-button>
                    <n-popconfirm
                      v-if="!r.systemRole && !r.immutable"
                      @positive-click="handleDelete(r.id)"
                    >
                      <template #trigger>
                        <n-button
                          size="tiny"
                          quaternary
                          type="error"
                          :disabled="r.userCount > 0"
                        >
                          {{ $t('common.delete') }}
                        </n-button>
                      </template>
                      {{ $t('adminRoles.confirmDelete') }}
                    </n-popconfirm>
                  </div>
                </td>
              </tr>
            </tbody>
          </n-table>
        </div>
      </n-spin>
    </n-card>

    <!-- Create / Edit Role Modal -->
    <n-modal
      v-model:show="showModal"
      preset="card"
      :title="editingRole ? $t('adminRoles.edit') : $t('adminRoles.create')"
      style="width: 600px; max-width: 95vw; border-radius: 12px;"
    >
      <n-form label-placement="left" :label-width="isMobile ? 'auto' : 120">
        <n-form-item>
          <template #label>
            <span style="display: inline-flex; align-items: center; gap: 6px;">
              {{ $t('adminRoles.name') }}
              <FieldHelp :text="$t('adminRoles.nameHelp')" />
            </span>
          </template>
          <n-input
            v-model:value="form.name"
            :disabled="Boolean(editingRole?.systemRole || editingRole?.immutable)"
          />
        </n-form-item>

        <n-form-item :label="$t('adminRoles.displayName')">
          <n-input v-model:value="form.displayName" :disabled="Boolean(editingRole?.immutable)" />
        </n-form-item>

        <n-form-item :label="$t('adminRoles.description')">
          <n-input v-model:value="form.description" :disabled="Boolean(editingRole?.immutable)" />
        </n-form-item>
      </n-form>

      <n-divider style="margin: 12px 0;">{{ $t('adminRoles.permissions') }}</n-divider>

      <div style="display: flex; flex-direction: column; gap: 12px;">
        <div v-for="perm in permDefs" :key="permKeyOf(perm)" class="perm-row">
          <div style="display: flex; align-items: center; gap: 6px;">
            <span style="font-weight: 500;">{{ permTitle(perm) }}</span>
            <FieldHelp :text="permHelp(perm)" />
          </div>

          <n-radio-group
            v-model:value="form.permissionLevels[permKeyOf(perm)]"
            size="small"
            :disabled="Boolean(editingRole?.immutable)"
          >
            <n-radio-button :value="0">{{ $t('home.levelDeny') }}</n-radio-button>
            <n-radio-button :value="1">{{ $t('home.levelReadonly') }}</n-radio-button>
            <n-radio-button :value="2">{{ $t('home.levelFull') }}</n-radio-button>
          </n-radio-group>
        </div>
      </div>

      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button v-if="!editingRole?.immutable" type="primary" :loading="saving" @click="handleSave">
            {{ $t('common.save') }}
          </n-button>
        </div>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { AddOutline } from '@vicons/ionicons5'
import FieldHelp from '../components/FieldHelp.vue'
import { adminApi, type RoleItem } from '../api/admin'
import type { PermissionDef } from '../api/auth'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { useSiteStore } from '../stores/site'

const message = useMessage()
const { t, te } = useI18n()
const authStore = useAuthStore()
const siteStore = useSiteStore()

const canWrite = computed(() => authStore.hasPermission('admin', 2))

const isMobile = ref(false)
function updateMobile() {
  isMobile.value = window.innerWidth <= 768
}

const loading = ref(false)
const saving = ref(false)
const roles = ref<RoleItem[]>([])
const permDefs = ref<PermissionDef[]>([])

const showModal = ref(false)
const editingRole = ref<RoleItem | null>(null)
const form = reactive<{
  name: string
  displayName: string
  description: string
  permissionLevels: Record<string, number>
}>({
  name: '',
  displayName: '',
  description: '',
  permissionLevels: {}
})

function permKeyOf(p: PermissionDef): string {
  return p.key || (p as any).permKey || ''
}

function permTitle(p: PermissionDef): string {
  const k = permKeyOf(p)
  const key = `adminRoles.perm_${k}`
  return te(key) ? t(key) : p.name || k
}

function permHelp(p: PermissionDef): string {
  const k = permKeyOf(p)
  const key = `adminRoles.perm_${k}Help`
  return te(key) ? t(key) : p.description || p.category || k
}

function levelLabel(lv: number): string {
  if (lv >= 2) return t('home.levelFull')
  if (lv >= 1) return t('home.levelReadonly')
  return t('home.levelDeny')
}

function levelTagType(lv: number): 'success' | 'warning' | 'default' {
  if (lv >= 2) return 'success'
  if (lv >= 1) return 'warning'
  return 'default'
}

async function loadData() {
  loading.value = true
  try {
    const [rRes, pRes] = await Promise.all([adminApi.listRoles(), adminApi.listPermissions()])
    roles.value = rRes.roles || []
    permDefs.value = pRes.permissions || []
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function buildDefaultLevels(existing?: Record<string, number>): Record<string, number> {
  const out: Record<string, number> = {}
  for (const def of permDefs.value) {
    const k = permKeyOf(def)
    if (k) out[k] = existing?.[k] ?? 0
  }
  return out
}

function openCreate() {
  editingRole.value = null
  form.name = ''
  form.displayName = ''
  form.description = ''
  form.permissionLevels = buildDefaultLevels()
  showModal.value = true
}

function openEdit(r: RoleItem) {
  editingRole.value = r
  form.name = r.name
  form.displayName = r.displayName
  form.description = r.description || ''
  form.permissionLevels = buildDefaultLevels(r.permissionLevels)
  showModal.value = true
}

async function handleSave() {
  saving.value = true
  try {
    if (editingRole.value) {
      await adminApi.updateRole(editingRole.value.id, {
        name: form.name,
        displayName: form.displayName,
        description: form.description,
        permissionLevels: form.permissionLevels
      })
      message.success(t('common.saved'))
    } else {
      await adminApi.createRole({
        name: form.name,
        displayName: form.displayName,
        description: form.description,
        permissionLevels: form.permissionLevels
      })
      message.success(t('common.created'))
    }
    showModal.value = false
    await Promise.all([loadData(), siteStore.refresh(), authStore.refreshMe()])
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await adminApi.deleteRole(id)
    message.success(t('common.deleted'))
    await loadData()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

onMounted(() => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  loadData()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

<style scoped>
.perm-row {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--usp-surface-subtle);
  border: 1px solid var(--usp-border);
}
</style>

