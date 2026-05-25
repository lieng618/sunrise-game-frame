import {createApp} from 'vue';
import {registerElementPlus} from '@/plugins/element-plus';
import router from '@/router';
import App from '@/App.vue';
import '@/assets/styles/index.css';

const app = createApp(App);
registerElementPlus(app);
app.use(router);
app.mount('#app');
