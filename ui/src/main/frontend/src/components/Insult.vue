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
import { QBtn, QToggle, QInput } from 'quasar'
import InsultService from 'assets/js/insult_service-proxy.js'
import EventBus from 'vertx3-eventbus-client'
import axios from 'axios'

export default {
  name: 'insult',
  components: {
    QBtn,
    QToggle,
    QInput
  },
  data() {
    return {
        nameInput: "",
        isReactiveEnabled: false,
        eventBus: {},
        service: {},
        rest: {},
        baseURL: "",
        insults: []
    }
  },
  methods: {
    getInsult() {
      var formatter = (res) => {
          var data = JSON.parse(res);
          var insult = "";
          if (data.subject===null) {
              insult = "Thou "+data.adj1+", "+data.adj2+", "+data.noun;
          } else {
              insult = data.subject+"; thou dost be a "+data.adj1+", "+data.adj2+", "+data.noun;
          }
          return insult;
      };
      if (this.isReactiveEnabled) {
          var resultHandler = (res, err) => {
              if (err===null) {
                  this.insults.push(formatter(res));
              } else {
                  console.log(err);
              }
          };
          if (nameInput==="") {
              service.getInsult(resultHandler);
          } else {
              service.namedInsult(this.nameInput, resultHandler);
          }
      } else {
          var reqPromise = {};
          if (nameInput==="") {
              reqPromise = this.rest.get("/insult");
          } else {
              reqPromise = this.rest.post("/insult", { name: this.nameInput });
          }
          reqPromise
              .then((resp) => {
                  this.insults.push(formatter(resp.data));
              })
              .catch((err) => {
                  console.log(err);
              });
      }
    }
  },
  created: function () {
    this.baseURL = window.base_url===""?"http://localhost:8080":window.base_url;
    this.eventBus = new EventBus(this.baseURL+"/eventbus");
    this.eventBus.onopen = () => {
        this.service = new InsultService(eb, "insult.service");
    };
    this.rest = axios.create({
        baseURL: this.baseURL,
        timeout: 1000
    });
  },
  destroyed: function () {
    this.eventBus.close();
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
