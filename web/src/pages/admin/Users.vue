<template>
  <div class="users-page">
    <div class="page-header">
      <h2>{{ $t('admin.users') }}</h2>
      <button class="btn-primary" @click="showCreateModal = true" v-if="auth.hasPermission('admin.users.edit')">+ {{ $t('admin.createUser') }}</button>
    </div>

    <table class="data-table" v-if="users.length">
      <thead>
        <tr>
          <th>ID</th><th>{{ $t('admin.username') }}</th><th>{{ $t('admin.role') }}</th><th>{{ $t('admin.binding') }}</th><th>{{ $t('admin.status') }}</th><th>{{ $t('admin.created') }}</th><th>{{ $t('admin.actions') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="u in users" :key="u.id">
          <td>{{ u.id }}</td>
          <td>{{ u.username }}</td>
          <td><span class="badge">{{ u.roleName }}</span></td>
          <td><span :class="u.bindingStatus === 'bound' ? 'text-green' : 'text-red'">{{ u.bindingStatus }}</span></td>
          <td><span :class="u.isActive ? 'text-green' : 'text-red'">{{ u.isActive ? 'Active' : 'Inactive' }}</span></td>
          <td>{{ formatDate(u.createdAt) }}</td>
          <td class="actions">
            <button class="btn-sm" @click="editUser(u)" v-if="auth.hasPermission('admin.users.edit')">Edit</button>
            <button class="btn-sm btn-danger" @click="confirmDelete(u)" v-if="auth.hasPermission('admin.users.edit') && u.id !== 1">Del</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="empty">No users found.</p>

    <!-- Create/Edit Modal -->
    <div class="modal-overlay" v-if="showCreateModal || showEditModal" @click.self="closeModals">
      <div class="modal">
        <h3>{{ showEditModal ? 'Edit User' : 'Create User' }}</h3>
        <div class="form-group" v-if="!showEditModal">
          <label>Username</label><input v-model="form.username" type="text" required />
        </div>
        <div class="form-group" v-if="!showEditModal">
          <label>Password</label><input v-model="form.password" type="password" required minlength="4" />
        </div>
        <div class="form-group">
          <label>Role</label>
          <select v-model="form.roleId">
            <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</option>
          </select>
        </div>
        <div class="form-group" v-if="showEditModal">
          <label>
            <input type="checkbox" v-model="form.isActive" /> Active
          </label>
        </div>
        <div class="form-group" v-if="showEditModal">
          <label>New Password (leave blank to keep)</label>
          <input v-model="form.newPassword" type="password" minlength="4" />
        </div>
        <div v-if="formError" class="error-msg">{{ formError }}</div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="closeModals">Cancel</button>
          <button class="btn-primary" @click="submitForm" :disabled="formLoading">{{ formLoading ? '...' : 'Save' }}</button>
        </div>
      </div>
    </div>

    <!-- Delete Confirm -->
    <div class="modal-overlay" v-if="showDeleteModal" @click.self="showDeleteModal = false">
      <div class="modal modal-sm">
        <h3>Delete User</h3>
        <p>Are you sure you want to delete "{{ deleteTarget?.username }}"?</p>
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
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../../stores/auth';
import { adminAPI } from '../../api/admin';

const auth = useAuthStore();
const users = ref([]);
const roles = ref([]);

const showCreateModal = ref(false);
const showEditModal = ref(false);
const showDeleteModal = ref(false);
const deleteTarget = ref(null);
const deleteError = ref('');
const formError = ref('');
const formLoading = ref(false);

const form = ref({ username: '', password: '', roleId: 2, isActive: true, newPassword: '' });
let editingId = null;

const loadData = async () => {
  try {
    const [uRes, rRes] = await Promise.all([adminAPI.listUsers(), adminAPI.listRoles()]);
    users.value = uRes.data.data.users;
    roles.value = rRes.data.data.roles;
  } catch (e) { /* ignore */ }
};

const editUser = (u) => {
  editingId = u.id;
  form.value = { username: u.username, password: '', roleId: u.roleId, isActive: u.isActive, newPassword: '' };
  showEditModal.value = true;
};

const submitForm = async () => {
  formError.value = '';
  formLoading.value = true;
  try {
    if (showEditModal.value) {
      const payload = { roleId: form.value.roleId, isActive: form.value.isActive };
      if (form.value.newPassword) payload.newPassword = form.value.newPassword;
      await adminAPI.updateUser(editingId, payload);
    } else {
      await adminAPI.createUser({ username: form.value.username, password: form.value.password, roleId: form.value.roleId });
    }
    closeModals();
    await loadData();
  } catch (e) {
    formError.value = e.response?.data?.message || 'Operation failed';
  } finally {
    formLoading.value = false;
  }
};

const confirmDelete = (u) => { deleteTarget.value = u; showDeleteModal.value = true; };
const doDelete = async () => {
  deleteError.value = '';
  try {
    await adminAPI.deleteUser(deleteTarget.value.id);
    showDeleteModal.value = false;
    await loadData();
  } catch (e) {
    deleteError.value = e.response?.data?.message || 'Delete failed';
  }
};

const closeModals = () => {
  showCreateModal.value = false; showEditModal.value = false;
  form.value = { username: '', password: '', roleId: 2, isActive: true, newPassword: '' };
  editingId = null; formError.value = '';
};

const formatDate = (ts) => new Date(ts * 1000).toLocaleDateString();

onMounted(loadData);
</script>

<style scoped>
.users-page { background: var(--card-bg); border-radius: 10px; padding: 20px; border: 1px solid var(--border-color); }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; color: var(--text-strong); }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th, .data-table td { padding: 10px 12px; text-align: left; border-bottom: 1px solid var(--border-color); font-size: 0.9rem; }
.data-table th { color: var(--text-secondary); font-weight: 600; }
.data-table td { color: var(--text-primary); }
.badge { background: var(--bg-color); padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; }
.text-green { color: #16a34a; }
.text-red { color: #dc2626; }
.actions { display: flex; gap: 6px; }
.btn-primary { padding: 8px 18px; background: var(--primary-color); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; }
.btn-secondary { padding: 8px 18px; background: var(--bg-color); color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 6px; cursor: pointer; }
.btn-sm { padding: 4px 10px; font-size: 0.8rem; border: 1px solid var(--border-color); border-radius: 4px; cursor: pointer; background: var(--bg-color); color: var(--text-primary); }
.btn-danger { background: #fef2f2; border-color: #fecaca; color: #dc2626; }
.btn-danger:hover { background: #fecaca; }
.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { background: var(--card-bg); border-radius: 10px; padding: 24px; width: 100%; max-width: 440px; }
.modal-sm { max-width: 360px; }
.modal h3 { margin: 0 0 16px; color: var(--text-strong); }
.form-group { margin-bottom: 12px; }
.form-group label { display: block; margin-bottom: 4px; font-size: 0.85rem; color: var(--text-secondary); }
.form-group input, .form-group select { width: 100%; padding: 8px 10px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-color); color: var(--text-strong); box-sizing: border-box; }
.modal-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.error-msg { background: #fef2f2; border: 1px solid #fecaca; color: #dc2626; padding: 8px 12px; border-radius: 6px; font-size: 0.85rem; margin-bottom: 12px; }
.empty { color: var(--text-secondary); text-align: center; padding: 40px; }
</style>
