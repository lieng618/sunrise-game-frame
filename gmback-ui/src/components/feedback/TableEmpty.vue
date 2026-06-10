<template>
  <div class="table-empty">
    <template v-if="error">
      <p class="table-empty__title">加载失败</p>
      <p class="table-empty__hint">{{ error }}</p>
    </template>
    <template v-else>
      <p class="table-empty__title">{{ emptyTitle }}</p>
      <p v-if="emptyHint" class="table-empty__hint">{{ emptyHint }}</p>
    </template>
    <el-button v-if="error" type="primary" link @click="$emit('retry')">重新加载</el-button>
  </div>
</template>

<script>
export default {
  name: 'TableEmpty',
  props: {
    error: {type: String, default: ''},
    emptyTitle: {type: String, default: '暂无数据'},
    emptyHint: {type: String, default: ''},
  },
  emits: ['retry'],
};
</script>
