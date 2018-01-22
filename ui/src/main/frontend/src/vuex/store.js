import Vue from 'vue'
import Vuex from 'vuex'
import axios from "axios/index";

Vue.use(Vuex);

const state = {
    serviceProxiesEnabled: false,
    currentInsult: '',
    currentSubject: '',
    apiBaseUrl: 'http://localhost:8080'
};

const mutations = {
    UPDATE_SUBJECT: (state, subject) => {
        state.subject = subject;
    },
    TOGGLE_SERVICE_PROXIES: (state) => {
        let newVal = !state.serviceProxyEnabled;
        state.serviceProxyEnabled = newVal;
    },
    UPDATE_INSULT: (state) => {
        const reqUrl = state.apiBaseUrl + "/insult";
        if (state.useServiceProxies) {
            // Use the service proxies to make the calls
        } else {
            if (state.subject === '') {
                axios.get(reqUrl).then(response => {
                    let insultData = JSON.parse(response.data);
                    let insult = "Thou " + insultData.adj.join(", ") + " " + insultData.noun;
                    state.insult = insult;
                }).catch(err => {
                    state.insult = '[request error]';
                    console.log(err);
                });
            } else {
                axios.post(reqUrl, {
                    body: {name: state.subject}
                }).then(response => {
                    let insultData = JSON.parse(response.data);
                    let insult = insultData.subject + ", thou art an" + insultData.adj.join(", ") + " " + insultData.noun;
                    state.insult = insult;
                }).catch(err => {
                    state.insult = '[request error]';
                    console.log(err);
                });
            }
        }
    },
    LOAD_CONFIG: (state) => {
        var configUrl = window.location.href + "/default.json";
        Axios.get(configUrl).then(response => {
            let apiConfig = JSON.parse(response.data);
            state.apiBaseUrl = apiConfig.apiBaseUrl;
        }).catch(e => {
            console.log("Error parsing response from we server for default configuration.", e);
        });
    }
};

export default new Vuex.Store({
    state,
    mutations
});