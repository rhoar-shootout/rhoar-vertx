<template>
  <div id="q-app">
    <header></header>
    <main>
      <img src="~assets/logo.png" alt="Red Hat OpenShift Application Runtimes - Insult UI" />
      <router-view></router-view>
    </main>
  </div>
</template>

<script>
/*
 * Root component
 */
export default {}
</script>

<style lang="stylus">
@import '~variables'

main
  text-align center
  margin-top 40px

header
  margin 0
  height 10px
  background-color $primary
</style>
