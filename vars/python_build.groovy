def call(dockerRepoName, imageName, portNum, service) {
    pipeline {
        agent any
        parameters {
            booleanParam(defaultValue: false, description: 'Deploy the App', name: 'DEPLOY')
        }
        stages {
            stage('Build') {
                steps {
                    cleanWs()
                    dir(service) {
                        sh 'if [ -d ".venv" ]; then rm -Rf .venv; fi'
                        sh 'python3 -m venv .venv'
                        sh '. ./.venv/bin/activate'
                        sh 'pip install -r requirements.txt --break-system-packages'
                        sh 'pip install --upgrade flask --break-system-packages'
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
                        //add snyk or safety?
                        sh 'echo hello'
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
                            sh "docker build -t ${dockerRepoName}:latest --tag stlouis9/${dockerRepoName}:${imageName} ."
                            sh "docker push stlouis9/${dockerRepoName}:${imageName}"
                        }
                    }
                }
            }
            stage('Deliver') {
                when {
                    expression { params.DEPLOY }
                }
                steps {
                    dir(service) {
                        sh "docker stop ${dockerRepoName} || true && docker rm ${dockerRepoName} || true"
                        sh "docker run -d -p ${portNum}:${portNum} --name ${dockerRepoName} ${dockerRepoName}:latest"
                    }
                }
            }
        }
    }
}
