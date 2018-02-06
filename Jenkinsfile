#!/usr/bin/groovy

/**
 this section of the pipeline executes on the master, which has a lot of useful variables that we can leverage to configure our pipeline
 **/
node (''){
    // these should align to the projects in the Application Inventory
    env.NAMESPACE = env.OPENSHIFT_BUILD_NAMESPACE.reverse().drop(6).reverse()
    env.DEV_PROJECT = "${env.NAMESPACE}-dev"
    env.DEMO_PROJECT = "${env.NAMESPACE}-demo"

    // this value should be set to the root directory of your source code within the git repository.
    // if the root of the source is the root of the repo, leave this value as ""
    env.SOURCE_CONTEXT_DIR = ""

    /**
     these are used to configure which repository maven deploys
     the ci-cd starter will create a nexus that has this repos available
     **/
    env.MVN_SNAPSHOT_DEPLOYMENT_REPOSITORY = "nexus::default::http://nexus:8081/repository/maven-snapshots"
    env.MVN_RELEASE_DEPLOYMENT_REPOSITORY = "nexus::default::http://nexus:8081/repository/maven-releases"

    // the complete build command
    // depending on the slave, you may need to wrap this command with scl enable
    env.BUILD_COMMAND = "mvn clean package dependency-check:check sonar:sonar vertx:package"

    // these are defaults that will help run openshift automation
    // DO NOT DELETE THESE - they are required
    env.OCP_API_SERVER = "${env.OPENSHIFT_API_URL}"
    env.OCP_TOKEN = readFile('/var/run/secrets/kubernetes.io/serviceaccount/token').trim()
}


/**
 this section of the pipeline executes on a custom mvn build slave.
 you should not need to change anything below unless you need new stages or new integrations (e.g. Cucumber Reports or Sonar)
 **/
node("jenkins-slave-mvn") {

    stage('SCM Checkout') {
        checkout scm
    }

    // List of modules to package as FAT JARs
    def modules = ['adjective','noun','insult'] as List

    dir ("${env.SOURCE_CONTEXT_DIR}") {

        stage('Build backend services') {
            withSonarQubeEnv {
                sh "${env.BUILD_COMMAND}"
            }
        }

        modules.each {
            // assumes uber jar is created
            stage('Build ${it}-app Image') {
                sh "oc start-build ${it}-app --from-file=${it}-app/target/${it}-app-${pom.version}.jar"
            }
        }
    }

    modules.each {
        // no user changes should be needed below this point
        stage ('Deploy ${it}-app to Dev') {
            openshiftTag (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", destStream: "${it}-app", destTag: 'latest', destinationAuthToken: "${env.OCP_TOKEN}", destinationNamespace: "${env.DEV_PROJECT}", namespace: "${env.OPENSHIFT_BUILD_NAMESPACE}", srcStream: "${it}-app", srcTag: 'latest')

            openshiftVerifyDeployment (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", depCfg: "${it}-app", namespace: "${env.DEV_PROJECT}", verifyReplicaCount: true)
        }
    }

    modules.each {
        stage('Deploy ${it}-app to Demo') {
            input "Promote Application to Demo?"

            openshiftTag(apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", destStream: "${it}-app", destTag: 'latest', destinationAuthToken: "${env.OCP_TOKEN}", destinationNamespace: "${env.DEMO_PROJECT}", namespace: "${env.DEV_PROJECT}", srcStream: "${it}-app", srcTag: 'latest')

            openshiftVerifyDeployment(apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", depCfg: "${it}-app", namespace: "${env.DEMO_PROJECT}", verifyReplicaCount: true)
        }
    }
}

node("jenkins-slave-npm") {
    stage('SCM Checkout') {
        checkout scm
    }

    stage ('Build UI') {
        dir ("${env.SOURCE_CONTEXT_DIR}/ui/src/main/frontend") {
            sh "npm install"
            sh "npm run build"
            sh "oc start-build insult-ui --from-dir=dist/ --follow"
        }
    }

    // no user changes should be needed below this point
    stage ('Deploy UI to Dev') {
        openshiftTag (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", destStream: "insult-ui", destTag: 'latest', destinationAuthToken: "${env.OCP_TOKEN}", destinationNamespace: "${env.DEV_PROJECT}", namespace: "${env.OPENSHIFT_BUILD_NAMESPACE}", srcStream: "insult-ui", srcTag: 'latest')

        openshiftVerifyDeployment (apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", depCfg: "insult-ui", namespace: "${env.DEV_PROJECT}", verifyReplicaCount: true)
    }

    stage('Deploy UI to Demo') {
        input "Promote Application to Demo?"

        openshiftTag(apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", destStream: "insult-ui", destTag: 'latest', destinationAuthToken: "${env.OCP_TOKEN}", destinationNamespace: "${env.DEMO_PROJECT}", namespace: "${env.DEV_PROJECT}", srcStream: "insult-ui", srcTag: 'latest')

        openshiftVerifyDeployment(apiURL: "${env.OCP_API_SERVER}", authToken: "${env.OCP_TOKEN}", depCfg: "insult-ui", namespace: "${env.DEMO_PROJECT}", verifyReplicaCount: true)
    }
}