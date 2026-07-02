<script setup>
// 全局应用入口 — 仅负责挂载路由视图
</script>

<template>
  <router-view />
</template>

<style>
/* 全局基础重置 — 与 style.css 配合使用 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

html, body {
  width: 100%;
  height: 100%;
  overflow-x: hidden;
}

#app {
  width: 100%;
  height: 100%;
}

a {
  text-decoration: none;
  color: inherit;
}

button {
  cursor: pointer;
}
</style>
