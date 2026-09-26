import { createApp } from 'vue'
import { createPinia } from 'pinia'
import PrimeVue from 'primevue/config'
import Aura from '@primevue/themes/aura'
import { definePreset } from '@primevue/themes'
import Tooltip from 'primevue/tooltip'
import ToastService from 'primevue/toastservice'
import App from './App.vue'
import router from './router'

import 'primeicons/primeicons.css'
import 'primeflex/primeflex.css'
import './style.css'

const MyPreset = definePreset(Aura, {
    semantic: {
        primary: {
            50: '{indigo.50}',
            100: '{indigo.100}',
            200: '{indigo.200}',
            300: '{indigo.300}',
            400: '{indigo.400}',
            500: '{indigo.500}',
            600: '{indigo.600}',
            700: '{indigo.700}',
            800: '{indigo.800}',
            900: '{indigo.900}',
            950: '{indigo.950}'
        },
        colorScheme: {
            light: {
                primary: {
                    color: '{indigo.900}',
                    inverseColor: '#ffffff',
                    hoverColor: '{indigo.800}',
                    activeColor: '{indigo.700}'
                },
                highlight: {
                    background: '{amber.500}',
                    focusBackground: '{amber.600}',
                    color: '#000000',
                    focusColor: '#000000'
                }
            },
            dark: {
                primary: {
                    color: '{amber.400}',
                    inverseColor: '{slate.950}',
                    hoverColor: '{amber.300}',
                    activeColor: '{amber.200}'
                },
                highlight: {
                    background: '{amber.400}',
                    focusBackground: '{amber.300}',
                    color: '#000000',
                    focusColor: '#000000'
                },
                surface: {
                    0: '#ffffff',
                    50: '{slate.50}',
                    100: '{slate.100}',
                    200: '{slate.200}',
                    300: '{slate.300}',
                    400: '{slate.400}',
                    500: '{slate.500}',
                    600: '{slate.600}',
                    700: '{slate.700}',
                    800: '{slate.800}',
                    900: '{slate.900}',
                    950: '{slate.950}'
                }
            }
        }
    }
});

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.directive('tooltip', Tooltip)
app.use(PrimeVue, { 
    theme: { 
        preset: MyPreset,
        options: {
            darkModeSelector: '.p-dark'
        }
    } 
})
app.use(ToastService)

app.mount('#app')
