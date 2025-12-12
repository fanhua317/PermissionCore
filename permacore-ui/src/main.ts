import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import { createPinia } from 'pinia';
import router from './router';
import App from './App.vue';

createApp(App)
  .use(ElementPlus)
  .use(createPinia())
  .use(router)
  .mount('#app');