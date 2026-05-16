import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router/index.js'
import i18n from './i18n.js'
import { vPermission } from './directives/permission.js'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(i18n)
app.directive('permission', vPermission)
app.mount('#app')
