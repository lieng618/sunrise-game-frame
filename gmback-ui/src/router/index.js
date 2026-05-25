import {createRouter, createWebHistory} from 'vue-router';
import {routes} from './routes.js';
import {setupRouterGuards} from './guards.js';

const router = createRouter({
    history: createWebHistory(),
    routes,
});

setupRouterGuards(router);

export default router;
