def call(dockerRepoName, service) {
    pipeline {
        agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }
        stages {
            stage('Build') {
                steps {
                    dir(service) {
                        sh 'if [ -d ".venv" ]; then rm -Rf .venv; fi'
                        sh 'python3 -m venv .venv'
                        sh '. ./.venv/bin/activate'
                        sh 'pip install -r requirements.txt --break-system-packages'
                        sh 'pip install --upgrade flask --break-system-packages'
                        sh 'pip install safety --break-system-packages'
                    }
                }
            }
            stage('Python Lint') {
                steps {
                    dir(service) {
                        sh 'pylint --fail-under 5 *.py'
                    }
                }
            }
            stage('Security Check') {
                steps {
                    dir(service) {
                        sh script: '''
                        . ./.venv/bin/activate
                        export PATH=$PATH:~/.local/bin
                        safety check -r requirements.txt --full-report > safety_report.txt
                        ''', returnStdout: false
                        archiveArtifacts artifacts: 'safety_report.txt', onlyIfSuccessful: false
                    }
                }
            }
            stage('Package') {
                when {
                    expression { env.GIT_BRANCH == 'origin/master' }
                }
                steps {
                    dir(service) {
                        withCredentials([string(credentialsId: 'DockerHub', variable: 'TOKEN')]) {
                            sh "docker login -u 'stlouis9' -p '$TOKEN' docker.io"
                            sh "docker build -t stlouis9/${dockerRepoName}:latest ."
                            sh "docker push stlouis9/${dockerRepoName}:latest"
                        }
                    }
                }
            }
            stage('Deliver') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    sshagent(['Robert3855VM']) {
                        sh """
                        ssh -o StrictHostKeyChecking=no azureuser@kafka3855.westus3.cloudapp.azure.com 'cd ~/3855Microservices/deployment && docker compose pull storage receiver processing && docker compose up -d'
                        """
                    }
                }
            }
        }
    }
}
