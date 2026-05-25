<template>
  <el-aside width="220px" class="sidebar-wrapper">
    <el-menu :default-active="activeMenuPath" class="flex-1" router>
      <el-menu-item v-for="item in menuItems" :key="item.key" :index="item.path">
        <el-icon>
          <component :is="menuIcons[item.key]"/>
        </el-icon>
        <span>{{ item.title }}</span>
      </el-menu-item>
    </el-menu>

    <div class="sidebar-footer">
      <a
          href="https://sunrise-game-frame.pages.dev"
          target="_blank"
          rel="noopener noreferrer"
          class="text-gray-500 hover:text-blue-600 transition-colors no-underline"
      >
        sunrise-game-frame@lieng
      </a>
    </div>
  </el-aside>
</template>

<script>
import {MENU_ICONS} from '@/constants/menu-icons';

export default {
  name: 'AppSidebar',
  props: {
    menuItems: {type: Array, required: true},
    activeMenuPath: {type: String, required: true},
  },
  setup() {
    return {menuIcons: MENU_ICONS};
  },
};
</script>
