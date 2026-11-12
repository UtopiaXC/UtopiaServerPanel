<template>
  <div>
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ $t('tokens.title') }}</h2>
        <div style="font-size: 13px; color: var(--usp-text-muted); margin-top: 4px;">
          {{ $t('tokens.subtitle') }}
        </div>
      </div>
      <n-button type="primary" @click="openCreate">
        <template #icon>
          <n-icon><AddOutline /></n-icon>
        </template>
        {{ $t('tokens.create') }}
      </n-button>
    </div>

    <n-card size="small" style="border-radius: 12px;">
      <div class="table-responsive">
        <n-table :bordered="false" :single-line="false">
          <thead>
            <tr>
              <th>{{ $t('tokens.name') }}</th>
              <th>{{ $t('tokens.prefix') }}</th>
              <th>{{ $t('tokens.lastUsed') }}</th>
              <th>{{ $t('tokens.expires') }}</th>
              <th>{{ $t('adminUsers.createdAt') }}</th>
              <th style="text-align: right;">{{ $t('common.actions') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="tk in tokens" :key="tk.id">
              <td><strong>{{ tk.name }}</strong></td>
              <td><code class="mono">{{ tk.prefix }}...</code></td>
              <td class="mono" style="font-size: 12px;">
                {{ tk.lastUsedAt ? formatTimestamp(tk.lastUsedAt) : $t('common.never') }}
              </td>
              <td class="mono" style="font-size: 12px;">
                {{ tk.expiresAt ? formatTimestamp(tk.expiresAt) : $t('tokens.neverExpires') }}
              </td>
              <td class="mono" style="font-size: 12px;">{{ formatTimestamp(tk.createdAt) }}</td>
              <td style="text-align: right;">
                <n-popconfirm @positive-click="revoke(tk.id)">
                  <template #trigger>
                    <n-button size="tiny" quaternary type="error">
                      {{ $t('tokens.revoke') }}
                    </n-button>
                  </template>
                  {{ $t('tokens.confirmRevoke') }}
                </n-popconfirm>
              </td>
            </tr>
            <tr v-if="tokens.length === 0">
              <td colspan="6" style="text-align: center; padding: 24px; color: var(--usp-text-muted);">
                {{ $t('common.noData') }}
              </td>
            </tr>
          </tbody>
        </n-table>
      </div>
    </n-card>

    <!-- Create Token Modal -->
    <n-modal
      v-model:show="showCreateModal"
      preset="card"
      :title="$t('tokens.create')"
      style="width: 420px; max-width: 94vw; border-radius: 12px;"
    >
      <n-form>
        <n-form-item :label="$t('tokens.name')">
          <n-input v-model:value="createForm.name" :placeholder="$t('tokens.namePlaceholder')" />
        </n-form-item>
        <n-form-item>
          <template #label>
            <span style="display: inline-flex; align-items: center; gap: 6px;">
              {{ $t('tokens.expiresDays') }}
              <FieldHelp :text="$t('tokens.expiresHelp')" />
            </span>
          </template>
          <n-input-number v-model:value="createForm.expiresDays" :min="0" style="width: 100%;" />
        </n-form-item>
      </n-form>

      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
          <n-button @click="showCreateModal = false">{{ $t('common.cancel') }}</n-button>
          <n-button type="primary" :loading="creating" @click="handleCreate">
            {{ $t('common.create') }}
          </n-button>
        </div>
      </template>
    </n-modal>

    <!-- Reveal Created Token Modal -->
    <n-modal
      v-model:show="showSecretModal"
      preset="card"
      :title="$t('tokens.title')"
      style="width: 480px; max-width: 94vw; border-radius: 12px;"
    >
      <n-alert type="warning" :bordered="false" style="margin-bottom: 14px;">
        {{ $t('tokens.createdSecret') }}
      </n-alert>

      <div style="display: flex; gap: 8px; align-items: center;">
        <n-input :value="createdSecret" readonly class="mono" />
        <n-button type="primary" @click="copySecret">{{ $t('common.copy') }}</n-button>
      </div>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { useI18n } from 'vue-i18n'
import { AddOutline } from '@vicons/ionicons5'
import FieldHelp from '../components/FieldHelp.vue'
import { profileApi, type ApiTokenItem } from '../api/profile'
import { getApiErrorMessage } from '../api/client'
import { formatTimestamp } from '../utils/format'

const message = useMessage()
const { t } = useI18n()

const tokens = ref<ApiTokenItem[]>([])
const showCreateModal = ref(false)
const creating = ref(false)
const showSecretModal = ref(false)
const createdSecret = ref('')

const createForm = reactive({
  name: '',
  expiresDays: 0
})

async function loadTokens() {
  try {
    const res = await profileApi.listTokens()
    tokens.value = res.tokens || []
  } catch {
    // ignore
  }
}

function openCreate() {
  createForm.name = ''
  createForm.expiresDays = 0
  showCreateModal.value = true
}

async function handleCreate() {
  if (!createForm.name.trim()) return
  creating.value = true
  try {
    const res = await profileApi.createToken(
      createForm.name.trim(),
      createForm.expiresDays > 0 ? createForm.expiresDays : null
    )
    showCreateModal.value = false
    createdSecret.value = res.token
    showSecretModal.value = true
    await loadTokens()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  } finally {
    creating.value = false
  }
}

async function revoke(id: number) {
  try {
    await profileApi.revokeToken(id)
    message.success(t('common.deleted'))
    await loadTokens()
  } catch (err) {
    message.error(getApiErrorMessage(err))
  }
}

async function copySecret() {
  try {
    await navigator.clipboard.writeText(createdSecret.value)
    message.success(t('common.copied'))
  } catch {
    message.info(createdSecret.value)
  }
}

onMounted(loadTokens)
</script>

