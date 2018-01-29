<template>
  <div class="insult">
    <div>
      <q-btn outline color="red">Insult Me!</q-btn>
    </div>
    <div class="serviceProxySwitch">
      <q-toggle v-model="isReactiveEnabled" label="Reactive"></q-toggle>
    </div>
  </div>
</template>

<script>
import { QBtn, QToggle } from 'quasar'
import InsultService from 'assets/js/insult_service.js'

export default {
  name: 'insult',
  components: {
    QBtn,
    QToggle
  },
  data() {
    return {
        isReactiveEnabled: false,
        eventBus: {},
        service: {}
    }
  },
  methods: {
    toggleServiceProxies() {
        this.isReactiveEnabled = !this.isReactiveEnabled;
    },
    getInsult() {
      if (this.isReactiveEnabled) {

      } else {

      }
    },
    postInsult() {
      if (this.isReactiveEnabled) {

      } else {

      }
    },
    created: function () {

    },
    destroyed: function () {

    }
  }
}
</script>

<!-- Add "scoped" attribute to limit CSS to this component only -->
<style lang="scss" scoped>
.insult {
  margin-top: 50px;
  a {
    color: #35495E;
  }
}

.serviceProxySwitch {
  margin-top: 100px;
}
</style>
