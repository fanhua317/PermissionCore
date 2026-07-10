import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import { createPinia } from 'pinia';
import router from './router';
import App from './App.vue';
import { useUserStore } from '@/store/user';
import { bindAuthStore } from '@/utils/request';

const app = createApp(App);
const pinia = createPinia();

app.use(ElementPlus);
app.use(pinia);

const userStore = useUserStore(pinia);
bindAuthStore({
  setToken: userStore.setToken,
  clearToken: userStore.clearToken,
});

app.use(router);
app.mount('#app');
