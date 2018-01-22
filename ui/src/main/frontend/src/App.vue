<template>
  <div id="app">
    <img src="./assets/logo.png">
    <ElizabethanInsult/>
  </div>
</template>

<script>
import ElizabethanInsult from './components/ElizabethanInsult'

export default {
  name: 'app',
  components: {
      ElizabethanInsult
  }
}
</script>

<style>
#app {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 60px;
}
</style>
