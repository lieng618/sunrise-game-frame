import tailwindcss from '@tailwindcss/vite';
import vue from '@vitejs/plugin-vue';
import {defineConfig, loadEnv} from 'vite';
import {dirname, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));

export default defineConfig(({mode}) => {
    const env = loadEnv(mode, __dirname, '');
    const apiTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8010';

    return {
        plugins: [vue(), tailwindcss()],
        resolve: {
            alias: {
                '@': resolve(__dirname, 'src'),
                vue: 'vue/dist/vue.esm-bundler.js',
            },
        },
        define: {
            __VUE_OPTIONS_API__: true,
            __VUE_PROD_DEVTOOLS__: mode !== 'production',
            __VUE_PROD_HYDRATION_MISMATCH_DETAILS__: false,
        },
        server: {
            port: Number(env.VITE_DEV_PORT || 5173),
            proxy: {
                '/api': {
                    target: apiTarget,
                    changeOrigin: true,
                },
            },
        },
        build: {
            outDir: 'dist',
            rollupOptions: {
                output: {
                    manualChunks(id) {
                        if (id.includes('node_modules/vue/') || id.includes('node_modules/@vue/')) {
                            return 'vue';
                        }
                        if (id.includes('node_modules/vue-router')) {
                            return 'vue-router';
                        }
                        if (id.includes('node_modules/element-plus')) {
                            return 'element-plus';
                        }
                        if (id.includes('@element-plus/icons-vue')) {
                            return 'icons';
                        }
                    },
                },
            },
        },
    };
});
