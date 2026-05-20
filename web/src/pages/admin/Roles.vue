<template>
  <div class="roles-page">
    <div class="page-header">
      <h2>{{ $t('admin.roles') }}</h2>
      <button class="btn-primary" @click="openCreate" v-if="auth.hasFullAccess('admin')">+ {{ $t('admin.createRole') }}</button>
    </div>

    <div class="role-cards" v-if="roles.length">
      <div class="role-card" v-for="r in roles" :key="r.id">
        <div class="card-header">
          <div>
            <strong>{{ r.name }}</strong>
            <span class="badge" v-if="r.isSystem">{{ $t('admin.roleSystem') }}</span>
          </div>
        </div>
        <div class="card-desc">{{ r.description }}</div>
        <div class="card-perms">
          <span v-for="key in permKeys" :key="key" class="perm-badge" :class="'perm-' + levelLabel(r.permissionLevels?.[key] || 0)">
            {{ $t('permissions.' + key) }}: {{ $t('permissions.levels.' + levelLabel(r.permissionLevels?.[key] || 0)) }}
          </span>
        </div>
        <div v-if="r.id === 2" class="guest-hint">{{ $t('admin.guestRoleHint') }}</div>
        <div class="card-actions" v-if="auth.hasFullAccess('admin')">
          <button class="btn-sm" @click="openEdit(r)" :disabled="r.isImmutable">{{ $t('common.edit') }}</button>
          <button class="btn-sm btn-danger" @click="confirmDelete(r)" :disabled="r.isSystem">{{ $t('common.delete') }}</button>
        </div>
      </div>
    </div>
    <p v-else class="empty">{{ $t('admin.noRoles') }}</p>

    <!-- Create/Edit Modal -->
    <div class="modal-overlay" v-if="showModal" @click.self="closeModal">
      <div class="modal">
        <h3>{{ editingRole ? $t('admin.editRole') : $t('admin.createRole') }}</h3>
        <div class="form-group">
          <label>{{ $t('admin.roleName') }}</label>
          <input v-model="form.name" type="text" required :disabled="editingRole?.isSystem" />
        </div>
        <div class="form-group">
          <label>{{ $t('admin.roleDescription') }}</label>
          <input v-model="form.description" type="text" />
        </div>
        <div v-if="editingRole?.isImmutable" class="warning-msg">{{ $t('admin.roleImmutable') }}</div>
        <div v-else class="permissions-section">
          <label>{{ $t('admin.rolePermissions') }}</label>
          <div class="perm-row" v-for="key in permKeys" :key="key">
            <span class="perm-label">{{ $t('permissions.' + key) }}</span>
            <div class="level-selector">
              <button v-for="lvl in levels" :key="lvl.value"
                :class="['level-btn', { active: form.levels[key] === lvl.value }, 'level-' + lvl.label]"
                @click="form.levels[key] = lvl.value">
                {{ $t('permissions.levels.' + lvl.label) }}
              </button>
            </div>
          </div>
        </div>
        <div v-if="formError" class="error-msg">{{ formError }}</div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModal">{{ $t('common.cancel') }}</button>
          <button class="btn-primary" @click="submitRole" :disabled="formLoading">{{ formLoading ? '...' : $t('common.save') }}</button>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div class="modal-overlay" v-if="showDeleteModal" @click.self="showDeleteModal = false">
      <div class="modal modal-sm">
        <h3>{{ $t('admin.deleteRole') }}</h3>
        <p>{{ $t('admin.deleteRoleConfirm') }} "{{ deleteTarget?.name }}"?</p>
        <div v-if="deleteError" class="error-msg">{{ deleteError }}</div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showDeleteModal = false">{{ $t('common.cancel') }}</button>
          <button class="btn-danger" @click="doDelete">{{ $t('common.delete') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useAuthStore } from '../../stores/auth';
import { adminAPI } from '../../api/admin';

const auth = useAuthStore();
const roles = ref([]);
const permKeys = ['admin', 'dashboard', 'terminal', 'logs'];
const levels = [
  { value: 0, label: 'deny' },
  { value: 1, label: 'readonly' },
  { value: 2, label: 'full' }
];

const showModal = ref(false);
const showDeleteModal = ref(false);
const deleteTarget = ref(null);
const deleteError = ref('');
const formError = ref('');
const formLoading = ref(false);
const editingRole = ref(null);

const form = reactive({
  name: '',
  description: '',
  levels: { admin: 0, dashboard: 0, terminal: 0, logs: 0 }
});

const levelLabel = (level) => {
  if (level >= 2) return 'full';
  if (level >= 1) return 'readonly';
  return 'deny';
};

const loadData = async () => {
  try {
    const rRes = await adminAPI.listRoles();
    roles.value = rRes.data.data.roles;
  } catch (e) { /* ignore */ }
};

const openCreate = () => {
  editingRole.value = null;
  form.name = ''; form.description = '';
  form.levels = { admin: 0, dashboard: 0, terminal: 0, logs: 0 };
  showModal.value = true;
};

const openEdit = async (role) => {
  editingRole.value = role;
  form.name = role.name;
  form.description = role.description || '';
  try {
    const { data } = await adminAPI.getRole(role.id);
    const pl = data.data.permissionLevels || {};
    form.levels = {
      admin: pl.admin || 0,
      dashboard: pl.dashboard || 0,
      terminal: pl.terminal || 0,
      logs: pl.logs || 0
    };
  } catch {
    form.levels = { admin: 0, dashboard: 0, terminal: 0, logs: 0 };
  }
  showModal.value = true;
};

const submitRole = async () => {
  formError.value = '';
  formLoading.value = true;
  try {
    const payload = { name: form.name, description: form.description, permissionLevels: { ...form.levels } };
    if (editingRole.value) {
      await adminAPI.updateRole(editingRole.value.id, payload);
    } else {
      await adminAPI.createRole(payload);
    }
    closeModal();
    await loadData();
  } catch (e) {
    formError.value = e.response?.data?.message || 'Operation failed';
  } finally {
    formLoading.value = false;
  }
};

const confirmDelete = (r) => { deleteTarget.value = r; showDeleteModal.value = true; };
const doDelete = async () => {
  deleteError.value = '';
  try {
    await adminAPI.deleteRole(deleteTarget.value.id);
    showDeleteModal.value = false;
    await loadData();
  } catch (e) { deleteError.value = e.response?.data?.message || 'Delete failed'; }
};

const closeModal = () => { showModal.value = false; editingRole.value = null; };

onMounted(loadData);
</script>

<style scoped>
.roles-page { background: var(--card-bg); border-radius: 10px; padding: 20px; border: 1px solid var(--border-color); }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; color: var(--text-strong); }
.role-cards { display: flex; flex-direction: column; gap: 10px; }
.role-card { border: 1px solid var(--border-color); border-radius: 8px; padding: 14px 16px; }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; font-size: 1rem; color: var(--text-strong); }
.card-desc { font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 8px; }
.card-perms { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 8px; }
.perm-badge { padding: 3px 10px; border-radius: 4px; font-size: 0.8rem; font-weight: 500; }
.perm-full { background: #dcfce7; color: #166534; }
.perm-readonly { background: #fef9c3; color: #854d0e; }
.perm-deny { background: #fef2f2; color: #991b1b; }
html.dark .perm-full { background: #14532d; color: #86efac; }
html.dark .perm-readonly { background: #422006; color: #fde047; }
html.dark .perm-deny { background: #450a0a; color: #fca5a5; }
.guest-hint { font-size: 0.8rem; color: var(--text-secondary); font-style: italic; margin-bottom: 8px; }
.card-actions { display: flex; gap: 6px; }
.badge { background: var(--bg-color); padding: 1px 6px; border-radius: 4px; font-size: 0.75rem; margin-left: 6px; color: var(--text-secondary); }

.permissions-section { margin-top: 12px; border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; }
.permissions-section > label { display: block; font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 10px; font-weight: 500; }
.perm-row { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--border-color); }
.perm-row:last-child { border-bottom: none; }
.perm-label { font-size: 0.9rem; color: var(--text-strong); font-weight: 500; }
.level-selector { display: flex; gap: 4px; }
.level-btn { padding: 4px 12px; border: 1px solid var(--border-color); border-radius: 4px; background: var(--card-bg); color: var(--text-secondary); cursor: pointer; font-size: 0.8rem; transition: all 0.15s; }
.level-btn:hover { border-color: var(--border-hover); }
.level-btn.active.level-deny { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.level-btn.active.level-readonly { background: #fef9c3; border-color: #fde68a; color: #854d0e; }
.level-btn.active.level-full { background: #dcfce7; border-color: #bbf7d0; color: #166534; }
html.dark .level-btn.active.level-deny { background: #450a0a; border-color: #7f1d1d; color: #fca5a5; }
html.dark .level-btn.active.level-readonly { background: #422006; border-color: #713f12; color: #fde047; }
html.dark .level-btn.active.level-full { background: #14532d; border-color: #166534; color: #86efac; }

.warning-msg { background: #fffbeb; border: 1px solid #fde68a; color: #92400e; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin: 12px 0; }
html.dark .warning-msg { background: #422006; border-color: #713f12; color: #fde047; }

.btn-primary { padding: 8px 18px; background: var(--primary-color); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }
.btn-secondary { padding: 8px 18px; background: var(--bg-color); color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 6px; cursor: pointer; }
.btn-sm { padding: 4px 10px; font-size: 0.8rem; border: 1px solid var(--border-color); border-radius: 4px; cursor: pointer; background: var(--bg-color); color: var(--text-primary); }
.btn-sm:disabled { opacity: 0.4; cursor: default; }
.btn-danger { background: #fef2f2; border-color: #fecaca; color: #dc2626; }
html.dark .btn-danger { background: #450a0a; border-color: #7f1d1d; color: #fca5a5; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--card-bg); border-radius: 10px; padding: 24px; width: 100%; max-width: 500px; max-height: 80vh; overflow-y: auto; }
.modal-sm { max-width: 360px; }
.modal h3 { margin: 0 0 16px; color: var(--text-strong); }
.modal p { color: var(--text-secondary); font-size: 0.9rem; }
.form-group { margin-bottom: 12px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 0.85rem; color: var(--text-secondary); }
.form-group input { width: 100%; padding: 8px 10px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-color); color: var(--text-strong); box-sizing: border-box; }
.form-group input:disabled { opacity: 0.5; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.error-msg { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.empty { color: var(--text-secondary); text-align: center; padding: 40px; }
</style>
