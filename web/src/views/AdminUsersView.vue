<template>
  <div>
    <div class="page-header">
      <h2 class="page-title">{{ $t('adminUsers.title') }}</h2>
      <n-button v-if="canWrite" type="primary" @click="openCreate">
        <template #icon>
          <n-icon><PersonAddOutline /></n-icon>
        </template>
        {{ $t('adminUsers.create') }}
      </n-button>
    </div>

    <!-- Filters -->
    <n-card size="small" style="border-radius: 12px; margin-bottom: 14px;">
      <div class="filter-bar">
        <n-input
          v-model:value="search"
          clearable
          :placeholder="$t('adminUsers.searchPlaceholder')"
          style="flex: 1; min-width: 210px;"
          @keyup.enter="handleFilterChange"
          @clear="handleFilterChange"
        >
          <template #prefix>
            <n-icon><SearchOutline /></n-icon>
          </template>
        </n-input>

        <n-select
          v-model:value="filterRoleId"
          clearable
          :placeholder="$t('adminUsers.filterRole')"
          :options="roleFilterOptions"
          style="width: 160px;"
          @update:value="handleFilterChange"
        />

        <n-select
          v-model:value="filterStatus"
          clearable
          :placeholder="$t('adminUsers.filterStatus')"
          :options="statusOptions"
          style="width: 150px;"
          @update:value="handleFilterChange"
        />

        <n-button secondary @click="handleFilterChange">
          {{ $t('common.search') }}
        </n-button>
      </div>
    </n-card>

    <!-- Users Table -->
    <n-card size="small" style="border-radius: 12px;">
      <n-spin :show="loading">
        <div class="table-responsive">
          <n-table :bordered="false" :single-line="false">
            <thead>
              <tr>
                <th>
                  <SortableHeader
                    title="ID"
                    column="id"
                    :current-sort-by="sortBy"
                    :current-order="sortOrder"
                    @sort="handleSort"
                  />
                </th>
                <th>
                  <SortableHeader
                    :title="$t('adminUsers.username')"
                    column="username"
                    :current-sort-by="sortBy"
                    :current-order="sortOrder"
                    @sort="handleSort"
                  />
                </th>
                <th>{{ $t('adminUsers.email') }}</th>
                <th>
                  <SortableHeader
                    :title="$t('adminUsers.role')"
                    column="roleId"
                    :current-sort-by="sortBy"
                    :current-order="sortOrder"
                    @sort="handleSort"
                  />
                </th>
                <th>
                  <SortableHeader
                    :title="$t('adminUsers.status')"
                    column="status"
                    :current-sort-by="sortBy"
                    :current-order="sortOrder"
                    @sort="handleSort"
                  />
                </th>
                <th>{{ $t('adminUsers.binding') }}</th>
                <th>{{ $t('adminUsers.twoFa') }}</th>
                <th>
                  <SortableHeader
                    :title="$t('adminUsers.createdAt')"
                    column="createdAt"
                    :current-sort-by="sortBy"
                    :current-order="sortOrder"
                    @sort="handleSort"
                  />
                </th>
                <th v-if="canWrite" style="text-align: right;">{{ $t('common.actions') }}</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="u in users" :key="u.id">
                <td>{{ u.id }}</td>
                <td>
                  <div style="font-weight: 600;">{{ u.username }}</div>
                  <div style="font-size: 12px; color: var(--usp-text-muted);">{{ u.nickname }}</div>
                </td>
                <td style="font-size: 13px; color: var(--usp-text-muted);">{{ u.email || '-' }}</td>
                <td>
                  <n-tag size="small" :bordered="false" :type="u.roleName === 'admin' ? 'warning' : 'info'">
                    {{ u.roleDisplayName || u.roleName }}
                  </n-tag>
                </td>
                <td>
                  <n-tag size="small" :bordered="false" :type="statusTagType(u.status)">
                    {{ statusLabel(u.status) }}
                  </n-tag>
                </td>
                <td>
                  <div v-if="u.bound" style="display: flex; align-items: center; gap: 6px;">
                    <n-tag size="small" type="success" :bordered="false">{{ u.playerName }}</n-tag>
                    <n-popconfirm v-if="canWrite" @positive-click="handleUnbind(u.id)">
                      <template #trigger>
                        <n-button size="tiny" quaternary type="warning">
                          {{ $t('adminUsers.unbindPlayer') }}
                        </n-button>
                      </template>
                      {{ $t('adminUsers.confirmUnbind') }}
                    </n-popconfirm>
                  </div>
                  <span v-else style="color: var(--usp-text-muted); font-size: 12px;">-</span>
                </td>
                <td>
                  <n-tag
                    size="small"
                    :bordered="false"
                    :type="u.totpEnabled ? 'success' : u.require2fa ? 'warning' : 'default'"
                  >
                    {{ u.totpEnabled ? $t('common.enabled') : $t('common.disabled') }}
                  </n-tag>
                </td>
                <td class="mono" style="font-size: 12px; white-space: nowrap;">
                  {{ formatTimestamp(u.createdAt) }}
                </td>
                <td v-if="canWrite" style="text-align: right; white-space: nowrap;">
                  <div style="display: inline-flex; gap: 6px;">
                    <n-button size="tiny" quaternary type="primary" @click="openEdit(u)">
                      {{ $t('common.edit') }}
                    </n-button>

                    <n-button
                      v-if="u.status === 'frozen' || isLockedOut(u)"
                      size="tiny"
                      quaternary
                      type="warning"
                      @click="handleUnlock(u.id)"
                    >
                      {{ $t('adminUsers.unlock') }}
                    </n-button>

                    <n-popconfirm v-if="u.totpEnabled" @positive-click="handleReset2fa(u.id)">
                      <template #trigger>
                        <n-button size="tiny" quaternary type="warning">
                          {{ $t('adminUsers.reset2fa') }}
                        </n-button>
                      </template>
                      {{ $t('adminUsers.confirmReset2fa') }}
                    </n-popconfirm>

                    <n-popconfirm
                      v-if="u.id !== authStore.user?.id"
                      @positive-click="handleDelete(u.id)"
                    >
                      <template #trigger>
                        <n-button size="tiny" quaternary type="error">
                          {{ $t('common.delete') }}
                        </n-button>
                      </template>
                      {{ $t('adminUsers.confirmDelete') }}
                    </n-popconfirm>
                  </div>
                </td>
              </tr>

              <tr v-if="users.length === 0">
                <td :colspan="canWrite ? 9 : 8" style="text-align: center; padding: 28px; color: var(--usp-text-muted);">
                  {{ $t('common.noData') }}
                </td>
              </tr>
            </tbody>
          </n-table>
        </div>

        <div v-if="total > pageSize" style="display: flex; justify-content: flex-end; margin-top: 14px;">
          <n-pagination
            v-model:page="page"
            :page-count="totalPages"
            size="small"
            @update:page="loadUsers"
          />
        </div>
      </n-spin>
    </n-card>

    <!-- Create User Modal -->
    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      :title="$t('adminUsers.create')"
      style="width: 520px; max-width: 94vw; border-radius: 12px;"
    >
      <n-form label-placement="left" :label-width="isMobile ? 'auto' : 120">
        <n-form-item :label="$t('adminUsers.username')">
          <n-input v-model:value="createForm.username" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.nickname')">
          <n-input v-model:value="createForm.nickname" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.email')">
          <n-input v-model:value="createForm.email" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.password')">
          <n-input v-model:value="createForm.password" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.role')">
          <n-select v-model:value="createForm.roleId" :options="assignableRoleOptions" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.status')">
          <n-select v-model:value="createForm.status" :options="statusOptions" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.mustChangePassword')">
          <n-switch v-model:value="createForm.mustChangePassword" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.require2fa')">
          <n-switch v-model:value="createForm.require2fa" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showCreateModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="saving" @click="handleCreate">
            {{ $t('common.create') }}
          </n-button>
        </div>
      </template>
    </n-modal>

    <!-- Edit User Modal -->
    <n-modal
      v-model:show="showEditModal"
      preset="card"
      :title="$t('adminUsers.edit')"
      style="width: 520px; max-width: 94vw; border-radius: 12px;"
    >
      <n-form label-placement="left" :label-width="isMobile ? 'auto' : 120">
        <n-form-item :label="$t('adminUsers.username')">
          <n-input :value="editTarget?.username" disabled />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.nickname')">
          <n-input v-model:value="editForm.nickname" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.email')">
          <n-input v-model:value="editForm.email" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.role')">
          <n-select
            v-model:value="editForm.roleId"
            :options="assignableRoleOptions"
            :disabled="editTarget?.id === authStore.user?.id"
          />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.status')">
          <n-select
            v-model:value="editForm.status"
            :options="statusOptions"
            :disabled="editTarget?.id === authStore.user?.id"
          />
        </n-form-item>
        <n-form-item>
          <template #label>
            <span style="display: inline-flex; align-items: center; gap: 6px;">
              {{ $t('adminUsers.newPassword') }}
              <FieldHelp :text="$t('adminUsers.newPasswordHelp')" />
            </span>
          </template>
          <n-input v-model:value="editForm.newPassword" type="password" show-password-on="click" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.mustChangePassword')">
          <n-switch v-model:value="editForm.mustChangePassword" />
        </n-form-item>
        <n-form-item :label="$t('adminUsers.require2fa')">
          <n-switch v-model:value="editForm.require2fa" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showEditModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="saving" @click="handleUpdate">
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
import { PersonAddOutline, SearchOutline } from '@vicons/ionicons5'
import SortableHeader from '../components/SortableHeader.vue'
import FieldHelp from '../components/FieldHelp.vue'
import { adminApi, type AdminUserItem, type RoleItem } from '../api/admin'
import { getApiErrorMessage } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { formatTimestamp } from '../utils/format'

const message = useMessage()
const { t } = useI18n()
const authStore = useAuthStore()

const canWrite = computed(() => authStore.hasPermission('admin', 2))

const isMobile = ref(false)
function updateMobile() {
  isMobile.value = window.innerWidth <= 768
}

const loading = ref(false)
const saving = ref(false)
const users = ref<AdminUserItem[]>([])
const roles = ref<RoleItem[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 20

const search = ref('')
const filterRoleId = ref<number | null>(null)
const filterStatus = ref<string | null>(null)
const sortBy = ref('id')
const sortOrder = ref<'asc' | 'desc' | null>('asc')

const showCreateModal = ref(false)
const createForm = reactive({
  username: '',
  nickname: '',
  email: '',
  password: '',
  roleId: 2,
  status: 'active',
  mustChangePassword: false,
  require2fa: false
})

const showEditModal = ref(false)
const editTarget = ref<AdminUserItem | null>(null)
const editForm = reactive({
  nickname: '',
  email: '',
  roleId: 2,
  status: 'active',
  newPassword: '',
  mustChangePassword: false,
  require2fa: false
})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const assignableRoles = computed(() => roles.value.filter((r) => r.name !== 'guest'))

const assignableRoleOptions = computed(() =>
  assignableRoles.value.map((r) => ({ label: r.displayName || r.name, value: r.id }))
)

const roleFilterOptions = computed(() =>
  assignableRoles.value.map((r) => ({ label: r.displayName || r.name, value: r.id }))
)

const statusOptions = computed(() => [
  { label: t('adminUsers.statusActive'), value: 'active' },
  { label: t('adminUsers.statusFrozen'), value: 'frozen' },
  { label: t('adminUsers.statusBanned'), value: 'banned' },
  { label: t('adminUsers.statusInactive'), value: 'inactive' }
])

function statusLabel(st: string) {
  switch (st) {
    case 'active':
      return t('adminUsers.statusActive')
    case 'frozen':
      return t('adminUsers.statusFrozen')
    case 'banned':
      return t('adminUsers.statusBanned')
    case 'inactive':
      return t('adminUsers.statusInactive')
    default:
      return st
  }
}

function statusTagType(st: string): 'success' | 'warning' | 'error' | 'default' {
  switch (st) {
    case 'active':
      return 'success'
    case 'frozen':
      return 'warning'
    case 'banned':
      return 'error'
    default:
      return 'default'
  }
}

function isLockedOut(u: AdminUserItem): boolean {
  const nowSec = Math.floor(Date.now() / 1000)
  return Boolean(u.lockoutUntil && u.lockoutUntil > nowSec)
}

async function loadRoles() {
  try {
    const res = await adminApi.listRoles()
    roles.value = res.roles || []
  } catch {
    // ignore
  }
}

async function loadUsers(targetPage = page.value) {
  page.value = targetPage
  loading.value = true
  try {
    const res = await adminApi.listUsers({
      search: search.value || undefined,
      roleId: filterRoleId.value,
      status: filterStatus.value,
      sortBy: sortOrder.value ? sortBy.value : undefined,
      sortOrder: sortOrder.value || undefined,
      page: page.value,
      size: pageSize
    })
    users.value = res.users || []
    total.value = res.total || 0
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  loadUsers(1)
}

function handleSort(column: string, order: 'asc' | 'desc' | null) {
  sortBy.value = column
  sortOrder.value = order
  loadUsers(1)
}

function openCreate() {
  const defRole = assignableRoles.value.find((r) => r.name === 'user')?.id ?? assignableRoles.value[0]?.id ?? 2
  createForm.username = ''
  createForm.nickname = ''
  createForm.email = ''
  createForm.password = ''
  createForm.roleId = defRole
  createForm.status = 'active'
  createForm.mustChangePassword = false
  createForm.require2fa = false
  showCreateModal.value = true
}

async function handleCreate() {
  saving.value = true
  try {
    await adminApi.createUser({ ...createForm })
    message.success(t('common.created'))
    showCreateModal.value = false
    await loadUsers(1)
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    saving.value = false
  }
}

function openEdit(u: AdminUserItem) {
  editTarget.value = u
  editForm.nickname = u.nickname
  editForm.email = u.email || ''
  editForm.roleId = u.roleId
  editForm.status = u.status
  editForm.newPassword = ''
  editForm.mustChangePassword = u.mustChangePassword
  editForm.require2fa = u.require2fa
  showEditModal.value = true
}

async function handleUpdate() {
  if (!editTarget.value) return
  saving.value = true
  try {
    await adminApi.updateUser(editTarget.value.id, {
      nickname: editForm.nickname,
      email: editForm.email,
      roleId: editForm.roleId,
      status: editForm.status,
      newPassword: editForm.newPassword || undefined,
      mustChangePassword: editForm.mustChangePassword,
      require2fa: editForm.require2fa
    })
    message.success(t('common.saved'))
    showEditModal.value = false
    await loadUsers()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  try {
    await adminApi.deleteUser(id)
    message.success(t('common.deleted'))
    await loadUsers()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleUnbind(id: number) {
  try {
    await adminApi.unbindUser(id)
    message.success(t('common.updated'))
    await loadUsers()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleReset2fa(id: number) {
  try {
    await adminApi.resetUser2fa(id)
    message.success(t('common.updated'))
    await loadUsers()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function handleUnlock(id: number) {
  try {
    await adminApi.unlockUser(id)
    message.success(t('common.updated'))
    await loadUsers()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

onMounted(async () => {
  updateMobile()
  window.addEventListener('resize', updateMobile)
  await loadRoles()
  await loadUsers(1)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateMobile)
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
</style>

