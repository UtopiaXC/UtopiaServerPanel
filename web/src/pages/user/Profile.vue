<template>
  <div class="user-profile">
    <h2>{{ $t('user.profile.title') }}</h2>

    <!-- Self Profile Section -->
    <div class="section">
      <h3>{{ $t('user.profile.accountSettings') }}</h3>

      <!-- Change Username -->
      <div class="form-group">
        <label>{{ $t('user.profile.changeUsername') }}</label>
        <div class="input-row">
          <input type="text" v-model="newUsername" :placeholder="auth.user?.username" />
          <button class="btn-primary" @click="submitUsername" :disabled="!newUsername || newUsername === auth.user?.username">
            {{ $t('common.save') }}
          </button>
        </div>
        <p v-if="usernameMsg" :class="usernameMsgType">{{ usernameMsg }}</p>
      </div>

      <!-- Change Password -->
      <div class="form-group">
        <label>{{ $t('user.profile.changePassword') }}</label>
        <div class="input-col">
          <input type="password" v-model="oldPassword" :placeholder="$t('user.profile.currentPassword')" />
          <input type="password" v-model="newPassword" :placeholder="$t('user.profile.newPassword')" />
          <input type="password" v-model="confirmPassword" :placeholder="$t('user.profile.confirmPassword')" />
          <button class="btn-primary" @click="submitPassword" :disabled="!oldPassword || !newPassword || !confirmPassword">
            {{ $t('common.save') }}
          </button>
        </div>
        <p v-if="passwordMsg" :class="passwordMsgType">{{ passwordMsg }}</p>
      </div>

      <!-- Player Binding -->
      <div class="form-group">
        <label>{{ $t('user.profile.playerBinding') }}</label>
        <div v-if="auth.user?.bindingStatus === 'bound'" class="binding-info">
          <span>{{ $t('user.profile.boundTo') }}: <strong>{{ auth.user?.playerName }}</strong></span>
          <button class="btn-danger" @click="showUnbindConfirm = true">{{ $t('user.profile.unbind') }}</button>
        </div>
        <div v-else>
          <router-link to="/user/bind" class="btn-primary">{{ $t('user.home.bindPlayer') }}</router-link>
        </div>
      </div>

    </div>

    <!-- Confirm Unbind Modal -->
    <div v-if="showUnbindConfirm" class="modal-overlay" @click.self="showUnbindConfirm = false">
      <div class="modal">
        <h3>{{ $t('user.profile.confirmUnbind') }}</h3>
        <p>{{ $t('user.profile.unbindWarning') }}</p>
        <div class="modal-actions">
          <button class="btn-secondary" @click="showUnbindConfirm = false">{{ $t('common.cancel') }}</button>
          <button class="btn-danger" @click="doUnbind">{{ $t('user.profile.unbind') }}</button>
        </div>
      </div>
    </div>


  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useAuthStore } from '../../stores/auth';
import { authAPI } from '../../api/auth';
import api from '../../api/index';

const auth = useAuthStore();

// Username change
const newUsername = ref('');
const usernameMsg = ref('');
const usernameMsgType = ref('success');

// Password change
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const passwordMsg = ref('');
const passwordMsgType = ref('success');

// Unbind
const showUnbindConfirm = ref(false);



const submitUsername = async () => {
  try {
    await auth.changeUsername(newUsername.value);
    usernameMsg.value = '';
    usernameMsgType.value = 'success';
    await auth.fetchUser();
    usernameMsg.value = 'OK';
    newUsername.value = '';
  } catch (e) {
    usernameMsg.value = e.response?.data?.message || 'Error';
    usernameMsgType.value = 'error';
  }
};

const submitPassword = async () => {
  if (newPassword.value !== confirmPassword.value) {
    passwordMsg.value = auth.$i18n?.t('user.profile.passwordMismatch') || 'Passwords do not match';
    passwordMsgType.value = 'error';
    return;
  }
  try {
    await auth.changePassword(oldPassword.value, newPassword.value);
    passwordMsg.value = 'OK';
    passwordMsgType.value = 'success';
    oldPassword.value = '';
    newPassword.value = '';
    confirmPassword.value = '';
  } catch (e) {
    passwordMsg.value = e.response?.data?.message || 'Error';
    passwordMsgType.value = 'error';
  }
};

const doUnbind = async () => {
  try {
    await api.post('/binding/unbind');
    showUnbindConfirm.value = false;
    await auth.fetchUser();
  } catch (e) {
    console.error('Unbind failed', e);
  }
};


</script>

<style scoped>
.user-profile { max-width: 900px; }
.user-profile h2 { margin: 0 0 24px; color: var(--text-strong); font-size: 1.4rem; }

.section { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 12px; padding: 20px; margin-bottom: 24px; }
.section h3 { margin: 0 0 16px; color: var(--text-strong); font-size: 1.1rem; border-bottom: 1px solid var(--border-color); padding-bottom: 10px; }

.form-group { margin-bottom: 20px; }
.form-group > label { display: block; color: var(--text-secondary); font-size: 0.9rem; margin-bottom: 8px; font-weight: 500; }

.input-row { display: flex; gap: 10px; }
.input-row input { flex: 1; }
.input-col { display: flex; flex-direction: column; gap: 8px; max-width: 400px; }

input, select { padding: 8px 12px; border: 1px solid var(--border-color); border-radius: 6px; background: var(--card-bg); color: var(--input-text); font-size: 0.9rem; }
input:focus, select:focus { outline: none; border-color: var(--primary-color); }

.btn-primary { padding: 8px 18px; background: var(--primary-color); color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; text-decoration: none; transition: opacity 0.2s; }
.btn-primary:hover { opacity: 0.9; }
.btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-secondary { padding: 8px 18px; background: var(--tab-bg); color: var(--text-primary); border: 1px solid var(--border-color); border-radius: 6px; cursor: pointer; font-size: 0.9rem; text-decoration: none; }
.btn-secondary:hover { background: var(--tab-hover); }

.btn-danger { padding: 8px 18px; background: #ef4444; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-size: 0.9rem; }
.btn-danger:hover { background: #dc2626; }
.btn-danger:disabled { opacity: 0.5; cursor: not-allowed; }

.btn-sm { padding: 4px 10px; font-size: 0.8rem; border: 1px solid var(--border-color); border-radius: 4px; background: var(--card-bg); color: var(--text-primary); cursor: pointer; margin-right: 4px; }
.btn-sm:hover { background: var(--tab-bg); }
.btn-sm.btn-danger { border: none; }

p.success { color: #16a34a; font-size: 0.85rem; margin: 6px 0 0; }
p.error { color: #ef4444; font-size: 0.85rem; margin: 6px 0 0; }

.binding-info { display: flex; align-items: center; gap: 16px; }
.binding-info span { color: var(--text-primary); }

.toolbar { margin-bottom: 16px; }

.table-container { overflow-x: auto; }
table { width: 100%; border-collapse: collapse; }
th, td { padding: 10px 14px; text-align: left; border-bottom: 1px solid var(--border-color); font-size: 0.9rem; }
th { background: var(--bg-color); color: var(--text-secondary); font-weight: 600; font-size: 0.8rem; text-transform: uppercase; letter-spacing: 0.5px; }
td { color: var(--text-primary); }
td.empty { text-align: center; color: var(--text-secondary); padding: 24px; }

.badge { padding: 2px 8px; border-radius: 4px; font-size: 0.8rem; background: var(--tab-bg); }
.status-ok { color: #16a34a; }
.status-warn { color: #d97706; }

.modal-overlay { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; justify-content: center; align-items: center; z-index: 1000; }
.modal { background: var(--card-bg); border-radius: 12px; padding: 24px; width: 90%; max-width: 440px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); }
.modal h3 { margin: 0 0 12px; color: var(--text-strong); }
.modal p { color: var(--text-secondary); margin: 0 0 16px; font-size: 0.9rem; }
.modal-body { display: flex; flex-direction: column; gap: 12px; margin-bottom: 16px; }
.form-field label { display: block; font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 4px; }
.form-field input, .form-field select { width: 100%; }
.modal-actions { display: flex; justify-content: flex-end; gap: 10px; }
</style>
