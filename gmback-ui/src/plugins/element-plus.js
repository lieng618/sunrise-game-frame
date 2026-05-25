import {
    ElAside,
    ElButton,
    ElCard,
    ElCheckbox,
    ElCheckboxGroup,
    ElContainer,
    ElDatePicker,
    ElDialog,
    ElDropdown,
    ElDropdownItem,
    ElDropdownMenu,
    ElForm,
    ElFormItem,
    ElHeader,
    ElIcon,
    ElInput,
    ElInputNumber,
    ElLoading,
    ElMain,
    ElMenu,
    ElMenuItem,
    ElMessage,
    ElMessageBox,
    ElOption,
    ElPagination,
    ElRadio,
    ElRadioGroup,
    ElSelect,
    ElSwitch,
    ElTable,
    ElTableColumn,
    ElTag,
} from 'element-plus';
import {
    Bell,
    Clock,
    Delete,
    Document,
    Edit,
    House,
    Key,
    Lock,
    Message,
    Microphone,
    Mute,
    Open,
    Plus,
    Promotion,
    Refresh,
    RefreshLeft,
    Setting,
    Switch,
    Ticket,
    Unlock,
    Upload,
    User,
    UserFilled,
} from '@element-plus/icons-vue';

/** 兼容各页 `ElementPlus.ElMessage` 写法 */
export const ElementPlus = {ElMessage, ElMessageBox};

const elementComponents = [
    ElAside,
    ElButton,
    ElCard,
    ElCheckbox,
    ElCheckboxGroup,
    ElContainer,
    ElDatePicker,
    ElDialog,
    ElDropdown,
    ElDropdownItem,
    ElDropdownMenu,
    ElForm,
    ElFormItem,
    ElHeader,
    ElIcon,
    ElInput,
    ElInputNumber,
    ElMain,
    ElMenu,
    ElMenuItem,
    ElOption,
    ElPagination,
    ElRadio,
    ElRadioGroup,
    ElSelect,
    ElSwitch,
    ElTable,
    ElTableColumn,
    ElTag,
];

const icons = {
    Bell,
    Clock,
    Delete,
    Document,
    Edit,
    House,
    Key,
    Lock,
    Message,
    Microphone,
    Mute,
    Open,
    Plus,
    Promotion,
    Refresh,
    RefreshLeft,
    Setting,
    Switch,
    Ticket,
    Unlock,
    Upload,
    User,
    UserFilled,
};

export function registerElementPlus(app) {
    for (const component of elementComponents) {
        app.use(component);
    }
    app.use(ElLoading);
    for (const [key, component] of Object.entries(icons)) {
        app.component(key, component);
    }
}

if (import.meta.env.DEV) {
    window.ElementPlus = ElementPlus;
}
