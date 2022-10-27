pipeline {
  agent {
    node {
      label 'maven'
    }

  }
  stages {
    stage('拉代码') {
      agent none
      steps {
        container('base') {
          git(url: env.GIT_URL, credentialsId: env.GIT_CREDENTIAL_ID, branch: env.BRANCH_NAME, changelog: true, poll: false)
        }

      }
    }

    stage('打标签') {
      agent none
      steps {
        container('maven') {
        // 时间戳
        script {
            TIMESTAMP = sh(returnStdout: true, script: "echo `date '+%Y%m%d%H%M%S'`")
        }
        // maven项目版本号
        script {
            PROJECT_VERSION = sh(returnStdout: true, script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout')
            GIT_TAG = "devops-${PROJECT_VERSION}-${TIMESTAMP}"
            env.PROJECT_VERSION = "${PROJECT_VERSION}"
        }
        // git tag
        withCredentials([usernamePassword(credentialsId: env.GIT_CREDENTIAL_ID, passwordVariable: 'GIT_PASSWORD', usernameVariable: 'GIT_USERNAME')]) {
        sh """
              git config --global user.email env.GIT_EMAIL
              git config --global user.name ${GIT_USERNAME}
              git tag -m 'kubesphere auto tag' -a ${GIT_TAG} 
              git push http://${GIT_CREDENTIAL_ID}:${GIT_PASSWORD}@${git} --tags
          """
        }
        }
        
      }
    }

    stage('maven编译 && docker 构建') {
      agent none
      steps {
        container('maven') {
          withCredentials([usernamePassword(credentialsId : env.DOCKERHUB_CREDENTIAL_ID ,passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,)]) {
            sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
          }
          sh 'mvn clean package -Ptest -DskipTests -Ddocker'
        }

      }
    }

    stage('docker push') {
      agent none
      steps {
        container('maven') {
          withCredentials([usernamePassword(credentialsId : env.DOCKERHUB_CREDENTIAL_ID ,passwordVariable : 'DOCKER_PASSWORD' ,usernameVariable : 'DOCKER_USERNAME' ,)]) {
            sh 'echo "$DOCKER_PASSWORD" | docker login $REGISTRY -u "$DOCKER_USERNAME" --password-stdin'
          }
         sh 'docker tag $DOCKER_IMAGE $REGISTRY/$DOCKERHUB_NAMESPACE/$APP_NAME:$PROJECT_VERSION-$BUILD_NUMBER'
         sh 'docker push $REGISTRY/$DOCKERHUB_NAMESPACE/$APP_NAME:$PROJECT_VERSION-$BUILD_NUMBER'
        }

      }
    }
    
    stage('部署测试环境web') {
      agent none
      steps {
        container('maven') {
        sh 'printenv'
        withCredentials([
                        kubeconfigFile(
                            credentialsId: env.KUBECONFIG_CREDENTIAL_ID,
                            variable: 'KUBECONFIG')
                            ]) {
                sh 'envsubst < $WEB_DEPLOY_FILE | kubectl apply -f -'
              }

            }
      }
    }
    
  }
  environment {
    GIT_CREDENTIAL_ID = 'liuhy25252'
    GIT_EMAIL = 'xxx@123.cn'
    KUBECONFIG_CREDENTIAL_ID = 'kubeconfig-test'
    DOCKERHUB_CREDENTIAL_ID = 'harbor-id'
    REGISTRY = 'harbor.dap.local'
    DOCKERHUB_NAMESPACE = 'demo'
    GITHUB_ACCOUNT = 'kubesphere'
    APP_NAME = 'data-standard'
    GIT = 'git address '
    GIT_URL = "http://${env.GIT}"
    BRANCH_NAME = 'test'
    DOCKER_IMAGE = 'harbor.dap.local/demo/saveas-es:0.0.1-RELEASE-Beta'
    WEB_DEPLOY_FILE = './deploy/deploy-test.yaml'
  }
}