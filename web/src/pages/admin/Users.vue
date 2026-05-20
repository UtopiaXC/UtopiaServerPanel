<template>
  <div class="admin-users">
    <div class="page-header">
      <h2>{{ $t('user.profile.userManagement') }}</h2>
      <button v-if="auth.hasFullAccess('admin')" class="btn-primary" @click="openCreateUser">{{ $t('user.profile.createUser') }}</button>
    </div>

    <div class="table-container">
      <table>
        <thead>
          <tr>
            <th>ID</th>
            <th>{{ $t('common.username') }}</th>
            <th>{{ $t('user.profile.roleName') }}</th>
            <th>{{ $t('user.profile.status') }}</th>
            <th>{{ $t('user.profile.binding') }}</th>
            <th v-if="auth.hasFullAccess('admin')">{{ $t('common.actions') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td>{{ u.id }}</td>
            <td>{{ u.username }}</td>
            <td><span class="badge">{{ u.roleName }}</span></td>
            <td>
              <span :class="u.isActive ? 'status-ok' : 'status-warn'">
                {{ u.isActive ? $t('user.profile.active') : $t('user.profile.inactive') }}
              </span>
            </td>
            <td>{{ $t('user.home.' + (u.bindingStatus || 'unbound')) }}</td>
            <td v-if="auth.hasFullAccess('admin')">
              <button class="btn-sm" @click="openEditUser(u)">{{ $t('common.edit') }}</button>
              <button class="btn-sm btn-danger" @click="confirmDeleteUser(u)" :disabled="u.id === 1">{{ $t('common.delete') }}</button>
            </td>
          </tr>
          <tr v-if="users.length === 0">
            <td :colspan="auth.hasFullAccess('admin') ? 6 : 5" class="empty">{{ $t('user.profile.noUsers') }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Edit/Create User Modal -->
    <div v-if="showUserModal" class="modal-overlay" @click.self="showUserModal = false">
      <div class="modal">
        <h3>{{ editingUser ? $t('user.profile.editUser') : $t('user.profile.createUser') }}</h3>
        <div class="modal-body">
          <div class="form-field">
            <label>{{ $t('common.username') }}</label>
            <input type="text" v-model="userForm.username" :disabled="!!editingUser" />
          </div>
          <div class="form-field">
            <label>{{ editingUser ? $t('user.profile.newPasswordOptional') : $t('user.profile.password') }}</label>
            <input type="password" v-model="userForm.password" />
          </div>
          <div class="form-field">
            <label>{{ $t('user.profile.roleName') }}</label>
            <select v-model="userForm.roleId">
              <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.name }}</option>
            </select>
          </div>
          <div v-if="editingUser" class="form-field checkbox-field">
            <label>
              <input type="checkbox" v-model="userForm.isActive" /> <span>{{ $t('user.profile.active') }}</span>
            </label>
          </div>
        </div>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showUserModal = false">{{ $t('common.cancel') }}</button>
          <button class="btn-primary" @click="saveUser">{{ $t('common.save') }}</button>
        </div>
      </div>
    </div>

    <!-- Confirm Delete User Modal -->
    <div v-if="showDeleteConfirm" class="modal-overlay" @click.self="showDeleteConfirm = false">
      <div class="modal">
        <h3>{{ $t('user.profile.deleteUser') }}</h3>
        <p>{{ $t('user.profile.deleteWarning', { username: deletingUser?.username }) }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showDeleteConfirm = false">{{ $t('common.cancel') }}</button>
          <button class="btn-danger" @click="doDeleteUser">{{ $t('common.delete') }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../../stores/auth';
import api from '../../api/index';

const auth = useAuthStore();

const users = ref([]);
const roles = ref([]);
const showUserModal = ref(false);
const editingUser = ref(null);
const userForm = ref({ username: '', password: '', roleId: 2, isActive: true });
const showDeleteConfirm = ref(false);
const deletingUser = ref(null);

const loadUsers = async () => {
  try {
    const { data } = await api.get('/admin/users');
    users.value = data.data.users || [];
  } catch { /* ignore */ }
};

const loadRoles = async () => {
  try {
    const { data } = await api.get('/admin/roles');
    roles.value = data.data.roles || [];
  } catch { /* ignore */ }
};

const openCreateUser = () => {
  editingUser.value = null;
  userForm.value = { username: '', password: '', roleId: 2, isActive: true };
  showUserModal.value = true;
};

const openEditUser = (u) => {
  editingUser.value = u;
  userForm.value = { username: u.username, password: '', roleId: u.roleId, isActive: u.isActive === 1 || u.isActive === true };
  showUserModal.value = true;
};

const saveUser = async () => {
  try {
    if (editingUser.value) {
      const body = { roleId: userForm.value.roleId, isActive: userForm.value.isActive ? 1 : 0 };
      if (userForm.value.password) body.newPassword = userForm.value.password;
      await api.put(`/admin/users/${editingUser.value.id}`, body);
    } else {
      await api.post('/admin/users', {
        username: userForm.value.username,
        password: userForm.value.password,
        roleId: userForm.value.roleId
      });
    }
    showUserModal.value = false;
    await loadUsers();
  } catch (e) {
    alert(e.response?.data?.message || 'Error');
  }
};

const confirmDeleteUser = (u) => {
  deletingUser.value = u;
  showDeleteConfirm.value = true;
};

const doDeleteUser = async () => {
  try {
    await api.delete(`/admin/users/${deletingUser.value.id}`);
    showDeleteConfirm.value = false;
    deletingUser.value = null;
    await loadUsers();
  } catch (e) {
    alert(e.response?.data?.message || 'Error');
  }
};

onMounted(async () => {
  if (auth.hasReadAccess('admin')) {
    await Promise.all([loadUsers(), loadRoles()]);
  }
});
</script>

<style scoped>
.admin-users {
  background: var(--card-bg);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  padding: 24px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid var(--border-color);
}
.page-header h2 {
  margin: 0;
  font-size: 1.25rem;
  color: var(--text-strong);
}

.table-container { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 12px 14px; text-align: left; border-bottom: 1px solid var(--border-color); font-size: 0.9rem; }
th { background: var(--bg-color); color: var(--text-secondary); font-weight: 600; font-size: 0.85rem; text-transform: uppercase; letter-spacing: 0.5px; }
td { color: var(--text-primary); }
td.empty { text-align: center; color: var(--text-secondary); padding: 32px; }

.badge { padding: 3px 8px; border-radius: 6px; font-size: 0.8rem; background: var(--tab-bg); font-weight: 500; }
.status-ok { color: #16a34a; font-weight: 500; }
.status-warn { color: #d97706; font-weight: 500; }

.btn-primary { padding: 8px 16px; background: var(--primary-color); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; font-weight: 500; transition: opacity 0.2s; }
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-secondary { padding: 8px 16px; background: var(--tab-bg); color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 6px; cursor: pointer; font-size: 0.9rem; font-weight: 500; }
.btn-secondary:hover { background: var(--tab-hover); }

.btn-danger { padding: 8px 16px; background: #ef4444; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; font-weight: 500; transition: background 0.2s; }
.btn-danger:hover { background: #dc2626; }
.btn-danger:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-sm { padding: 4px 10px; font-size: 0.8rem; border: 1px solid var(--border-color); border-radius: 4px; background: var(--card-bg); color: var(--text-primary); cursor: pointer; margin-right: 6px; font-weight: 500; transition: background 0.2s; }
.btn-sm:hover { background: var(--tab-bg); }
.btn-sm.btn-danger { border: 1px solid #fecaca; color: #dc2626; background: transparent; }
.btn-sm.btn-danger:hover { background: #fef2f2; }
html.dark .btn-sm.btn-danger { border-color: #7f1d1d; }
html.dark .btn-sm.btn-danger:hover { background: #450a0a; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.6); display: flex; justify-content: center; align-items: center; z-index: 1000; backdrop-filter: blur(2px); }
.modal { background: var(--card-bg); border-radius: 12px; padding: 24px; width: 90%; max-width: 440px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); border: 1px solid var(--border-color); }
.modal h3 { margin: 0 0 16px; color: var(--text-strong); font-size: 1.2rem; }
.modal p { color: var(--text-secondary); margin: 0 0 20px; font-size: 0.95rem; line-height: 1.5; }
.modal-body { display: flex; flex-direction: column; gap: 16px; margin-bottom: 24px; }
.form-field label { display: block; font-size: 0.9rem; color: var(--text-secondary); margin-bottom: 6px; font-weight: 500; }
.form-field input, .form-field select { width: 100%; padding: 10px 12px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-color); color: var(--input-text); font-size: 0.95rem; }
.form-field input:focus, .form-field select:focus { outline: none; border-color: var(--primary-color); }
.checkbox-field label { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.checkbox-field input { width: auto; margin: 0; cursor: pointer; }
.checkbox-field span { font-size: 0.95rem; color: var(--text-strong); }
.modal-actions { display: flex; justify-content: flex-end; gap: 12px; }
</style>
