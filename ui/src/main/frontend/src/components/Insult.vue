<template>
  <div>
    <div class="row inline justify-center items-start">
      <div class="col-6" style="padding-right: 4px;">
        <q-input v-model="nameInput" type="text" placeholder="Target"></q-input>
        <q-btn outline color="red" @click="getInsult">Insult Me!</q-btn>
        <q-btn outline color="green" @click="clearHistory">I Didn't Mean It!</q-btn>
      </div>
      <div class="col-6" style="padding-left: 4px;">
        <textarea cols="60" rows="10" v-model="displayInsults"></textarea>
      </div>
    </div>
    <div class="row">
      <div class="col-12" style="padding-top: 20px">
        <q-toggle v-model="isReactiveEnabled">Reactive</q-toggle>
      </div>
    </div>
  </div>
</template>

<script>
import { QBtn, QToggle, QInput } from 'quasar'
import EventBus from 'vertx3-eventbus-client'
import InsultService from 'assets/insult_service-proxy'
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
  computed: {
      displayInsults() {
          return this.insults.join("\n");
      }
  },
  methods: {
    clearHistory() {
        this.insults = [];
    },
    getInsult() {
      var formatter = (res) => {
          var data = JSON.parse(res);
          var insult = "";
          if (data.subject===null) {
              insult = "Thou "+data.adj1+", "+data.adj2+", "+data.noun;
          } else {
              insult = data.subject+"; thou dost be a "+data.adj1+", "+data.adj2+", "+data.noun;
          }
          console.log("Insult formatted: "+insult);
          return insult;
      };
      if (this.isReactiveEnabled) {
          var resultHandler = (err, res) => {
              console.log("Error: " + JSON.stringify(err));
              console.log("Result: " + JSON.stringify(res));
              if (err===null) {
                  if (this.insults.length == 10) {
                      this.insults.pop();
                  }
                  this.insults.push(formatter(JSON.stringify(res)));
              } else {
                  console.log("Error calling service: "+err);
              }
          };
          if (this.nameInput==="" || this.nameInput === null) {
              this.service.getInsult(resultHandler);
          } else {
              this.service.namedInsult(this.nameInput, resultHandler);
          }
      } else {
          var reqPromise = {};
          if (this.nameInput==="" || this.nameInput === null) {
              reqPromise = this.rest.get("/insult");
          } else {
              reqPromise = this.rest.post("/insult", { name: this.nameInput });
          }
          reqPromise
              .then((resp) => {
                  if (this.insults.length == 10) {
                      this.insults.pop();
                  }
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
    console.log("BaseURL: "+this.baseURL);

    var options = {
        vertxbus_reconnect_attempts_max: Infinity, // Max reconnect attempts
        vertxbus_reconnect_delay_min: 1000, // Initial delay (in ms) before first reconnect attempt
        vertxbus_reconnect_delay_max: 10000, // Max delay (in ms) between reconnect attempts
        vertxbus_reconnect_exponent: 2, // Exponential backoff factor
        vertxbus_randomization_factor: 0.5 // Randomization factor between 0 and 1
    };

    this.eventBus = new EventBus(this.baseURL+"/eventbus", options);
    this.eventBus.enableReconnect(true);

    this.eventBus.onopen = () => {
        console.log("BaseURL: "+this.baseURL);
        this.service = new InsultService(this.eventBus, "insult.service");
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
.row {
  flex: auto;
}
</style>
