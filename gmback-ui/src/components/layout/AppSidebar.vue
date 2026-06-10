<template>
  <el-aside width="220px" class="sidebar-wrapper">
    <div class="logo-area">
      <p class="logo-title"><span class="logo-accent">Sunrise</span> GM</p>
    </div>
    <nav aria-label="主导航">
      <el-menu :default-active="activeMenuPath" class="flex-1" router>
        <el-menu-item
            v-for="item in menuItems"
            :key="item.key"
            :index="item.path"
            :aria-current="activeMenuPath === item.path ? 'page' : undefined"
        >
          <el-icon aria-hidden="true">
            <component :is="menuIcons[item.key]"/>
          </el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </nav>

    <div class="sidebar-footer">
      <a
          href="https://sunrise-game-frame.pages.dev"
          target="_blank"
          rel="noopener noreferrer"
          aria-label="打开 Sunrise 文档站（新窗口）"
      >
        文档站
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
