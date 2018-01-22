<template>
  <div class="hello">
      <input type="text" size="50" id="name" name="name" :value="currentSubject" @input="updateSubject" />
      <button id="submit" name="submit" @click="updateInsult">Insult Me</button>
      <div class="toggle">
          <input type="checkbox" name="toggleServiceProxies" v-bind:checked="serviceProxiesEnabled" @click="toggleServiceProxies"/>
      </div>
  </div>
</template>

<script>
import {updateSubject, updateInsult, toggleServiceProxies} from "@/vuex/actions";

export default {
    name: 'ElizabethanInsults',
    vuex: {
        getters: {
            serviceProxiesEnabled: state => state.serviceProxiesEnabled,
            currentInsult: state => state.currentInsult,
            currentSubject: state => state.currentSubject
        },
        actions: {
            updateSubject,
            updateInsult,
            toggleServiceProxies
        }
    }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style scoped>
h1, h2 {
  font-weight: normal;
}
ul {
  list-style-type: none;
  padding: 0;
}
li {
  display: inline-block;
  margin: 0 10px;
}
a {
  color: #42b983;
}
.toggle {
    margin-top: 40px;
}
</style>
