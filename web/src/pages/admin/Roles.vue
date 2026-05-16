<template>
  <div class="roles-page">
    <div class="page-header">
      <h2>{{ $t('admin.roles') }}</h2>
      <button class="btn-primary" @click="openCreate" v-if="auth.hasPermission('admin.roles.edit')">+ {{ $t('admin.createRole') }}</button>
    </div>

    <div class="role-cards" v-if="roles.length">
      <div class="role-card" v-for="r in roles" :key="r.id">
        <div class="card-header">
          <div>
            <strong>{{ r.name }}</strong>
            <span class="badge" v-if="r.isSystem">System</span>
            <span class="badge" v-if="r.isImmutable">Immutable</span>
          </div>
          <div class="card-meta">{{ r.userCount }} users · {{ r.permissionCount }} permissions</div>
        </div>
        <div class="card-desc">{{ r.description }}</div>
        <div class="card-actions" v-if="auth.hasPermission('admin.roles.edit')">
          <button class="btn-sm" @click="openEdit(r)" :disabled="r.isImmutable && !auth.isAdmin">Edit</button>
          <button class="btn-sm btn-danger" @click="confirmDelete(r)" :disabled="r.isSystem || r.userCount > 0">Delete</button>
        </div>
      </div>
    </div>
    <p v-else class="empty">No roles found.</p>

    <!-- Create/Edit Modal -->
    <div class="modal-overlay" v-if="showModal" @click.self="closeModal">
      <div class="modal modal-wide">
        <h3>{{ editingRole ? 'Edit Role' : 'Create Role' }}</h3>
        <div class="form-group">
          <label>Name</label><input v-model="form.name" type="text" required />
        </div>
        <div class="form-group">
          <label>Description</label><input v-model="form.description" type="text" />
        </div>
        <div v-if="editingRole?.isImmutable && !auth.isAdmin" class="warning-msg">This role's permissions are immutable.</div>
        <div class="permissions-section" v-if="!editingRole || !editingRole.isImmutable || auth.isAdmin">
          <label>Permissions</label>
          <div class="perm-module" v-for="(groups, module) in permissions" :key="module">
            <div class="perm-module-header" @click="toggleModule(module)">
              <span class="arrow">{{ expandedModules[module] ? '▼' : '▶' }}</span>
              <label class="module-check">
                <input type="checkbox" :checked="isModuleChecked(module)" @change="toggleAllModule(module, $event)" @click.stop />
                <strong>{{ module }}</strong>
              </label>
            </div>
            <div v-if="expandedModules[module]" class="perm-groups">
              <div class="perm-group" v-for="(perms, group) in groups" :key="group">
                <label class="group-check">
                  <input type="checkbox" :checked="isGroupChecked(module, group)" @change="toggleGroup(module, group, $event)" />
                  {{ group }}
                </label>
                <div class="perm-items">
                  <label class="perm-item" v-for="p in perms" :key="p.key">
                    <input type="checkbox" :checked="selectedPerms.has(p.key)" @change="togglePerm(p.key)" />
                    <span>{{ p.type }}</span>
                    <span class="perm-desc">{{ p.description }}</span>
                  </label>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div v-if="formError" class="error-msg">{{ formError }}</div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModal">Cancel</button>
          <button class="btn-primary" @click="submitRole" :disabled="formLoading">{{ formLoading ? '...' : 'Save' }}</button>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div class="modal-overlay" v-if="showDeleteModal" @click.self="showDeleteModal = false">
      <div class="modal modal-sm">
        <h3>Delete Role</h3>
        <p>Delete "{{ deleteTarget?.name }}"?</p>
        <div v-if="deleteError" class="error-msg">{{ deleteError }}</div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showDeleteModal = false">Cancel</button>
          <button class="btn-danger" @click="doDelete">Delete</button>
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
const permissions = ref({});
const allKeys = ref([]);

const showModal = ref(false);
const showDeleteModal = ref(false);
const deleteTarget = ref(null);
const deleteError = ref('');
const formError = ref('');
const formLoading = ref(false);
const editingRole = ref(null);
const selectedPerms = ref(new Set());
const expandedModules = reactive({});

const form = reactive({ name: '', description: '' });

const loadData = async () => {
  try {
    const [rRes, pRes] = await Promise.all([adminAPI.listRoles(), adminAPI.listPermissions()]);
    roles.value = rRes.data.data.roles;
    permissions.value = pRes.data.data.permissions;
    allKeys.value = pRes.data.data.allKeys;
  } catch (e) { /* ignore */ }
};

const openCreate = () => {
  editingRole.value = null;
  form.name = ''; form.description = '';
  selectedPerms.value = new Set();
  showModal.value = true;
};

const openEdit = async (role) => {
  editingRole.value = role;
  form.name = role.name;
  form.description = role.description || '';
  try {
    const { data } = await adminAPI.getRole(role.id);
    selectedPerms.value = new Set(data.data.permissionKeys || []);
  } catch { selectedPerms.value = new Set(); }
  showModal.value = true;
};

const toggleModule = (m) => { expandedModules[m] = !expandedModules[m]; };

const isModuleChecked = (module) => {
  const keys = getModuleKeys(module);
  return keys.length > 0 && keys.every(k => selectedPerms.value.has(k));
};
const toggleAllModule = (module, e) => {
  const keys = getModuleKeys(module);
  if (e.target.checked) keys.forEach(k => selectedPerms.value.add(k));
  else keys.forEach(k => selectedPerms.value.delete(k));
};
const isGroupChecked = (module, group) => {
  const groupPerms = permissions.value[module]?.[group] || [];
  return groupPerms.length > 0 && groupPerms.every(p => selectedPerms.value.has(p.key));
};
const toggleGroup = (module, group, e) => {
  const groupPerms = permissions.value[module]?.[group] || [];
  if (e.target.checked) groupPerms.forEach(p => selectedPerms.value.add(p.key));
  else groupPerms.forEach(p => selectedPerms.value.delete(p.key));
};
const togglePerm = (key) => {
  if (selectedPerms.value.has(key)) selectedPerms.value.delete(key);
  else selectedPerms.value.add(key);
};
const getModuleKeys = (module) => {
  const groups = permissions.value[module] || {};
  return Object.values(groups).flat().map(p => p.key);
};

const submitRole = async () => {
  formError.value = '';
  formLoading.value = true;
  try {
    const payload = { name: form.name, description: form.description, permissionKeys: [...selectedPerms.value] };
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
.card-meta { font-size: 0.8rem; color: var(--text-secondary); }
.card-desc { font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 8px; }
.card-actions { display: flex; gap: 6px; }
.badge { background: var(--bg-color); padding: 1px 6px; border-radius: 4px; font-size: 0.75rem; margin-left: 6px; color: var(--text-secondary); }
.warning-msg { background: #fffbeb; border: 1px solid #fde68a; color: #92400e; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.btn-primary { padding: 8px 18px; background: var(--primary-color); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; }
.btn-secondary { padding: 8px 18px; background: var(--bg-color); color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 6px; cursor: pointer; }
.btn-sm { padding: 4px 10px; font-size: 0.8rem; border: 1px solid var(--border-color); border-radius: 4px; cursor: pointer; background: var(--bg-color); color: var(--text-primary); }
.btn-sm:disabled { opacity: 0.4; cursor: default; }
.btn-danger { background: #fef2f2; border-color: #fecaca; color: #dc2626; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--card-bg); border-radius: 10px; padding: 24px; width: 100%; max-width: 440px; max-height: 80vh; overflow-y: auto; }
.modal-wide { max-width: 600px; }
.modal-sm { max-width: 360px; }
.modal h3 { margin: 0 0 16px; color: var(--text-strong); }
.form-group { margin-bottom: 12px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 0.85rem; color: var(--text-secondary); }
.form-group input, .form-group select { width: 100%; padding: 8px 10px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-color); color: var(--text-strong); box-sizing: border-box; }
.permissions-section { margin-top: 12px; border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; max-height: 300px; overflow-y: auto; }
.perm-module { margin-bottom: 8px; }
.perm-module-header { display: flex; align-items: center; gap: 6px; cursor: pointer; padding: 4px 0; }
.arrow { font-size: 0.7rem; width: 14px; color: var(--text-secondary); }
.module-check { display: flex; align-items: center; gap: 6px; cursor: pointer; }
.perm-groups { margin-left: 20px; }
.perm-group { margin: 4px 0; }
.group-check { display: block; cursor: pointer; font-size: 0.85rem; color: var(--text-secondary); margin: 4px 0; }
.perm-items { margin-left: 20px; }
.perm-item { display: flex; align-items: center; gap: 6px; font-size: 0.8rem; color: var(--text-secondary); cursor: pointer; padding: 2px 0; }
.perm-desc { color: var(--text-secondary); opacity: 0.6; font-size: 0.75rem; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.error-msg { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.empty { color: var(--text-secondary); text-align: center; padding: 40px; }
</style>
