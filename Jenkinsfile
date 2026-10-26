// Loads KEY=VALUE lines from a .env file into the pipeline environment
def loadEnvFile(String path) {
    for (String rawLine : readFile(path).split('\n')) {
        String line = rawLine.trim()
        int eq = line.indexOf('=')
        if (eq > 0 && !line.startsWith('#')) {
            env."${line.substring(0, eq).trim()}" = line.substring(eq + 1).trim()
        }
    }
}

pipeline {
    agent any

    environment {
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        // Jenkins credential IDs; every other setting lives in .env
        DOCKERHUB_CREDENTIALS = 'dockerhub-credentials'
        ENV_FILE_CREDENTIALS  = 'calculator-env'
    }

    triggers {
        // Fires on GitHub webhook (requires "GitHub hook trigger" in the job config)
        githubPush()
        // Fallback in case the webhook is unreachable
        pollSCM('H/5 * * * *')
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                // .env is gitignored, so Jenkins keeps its copy as a Secret file credential
                withCredentials([file(credentialsId: env.ENV_FILE_CREDENTIALS, variable: 'ENV_FILE')]) {
                    // The secret file is read-only, so remove the previous build's copy first
                    sh 'rm -f .env && cp "$ENV_FILE" .env'
                }
                script {
                    loadEnvFile('.env')
                }
            }
        }

        stage('Build') {
            steps {
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn -B test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t $DOCKER_IMAGE:$IMAGE_TAG -t $DOCKER_IMAGE:latest .'
            }
        }

        stage('Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: env.DOCKERHUB_CREDENTIALS,
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push $DOCKER_IMAGE:$IMAGE_TAG
                        docker push $DOCKER_IMAGE:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                // up -d only recreates containers whose image/config changed, so re-runs are safe
                sh 'docker compose up -d --no-build --remove-orphans'
                sh '''
                    for i in $(seq 1 30); do
                        if curl -fs http://localhost:$HOST_PORT/actuator/health | grep -q UP; then
                            echo "Deployment healthy"
                            curl -fs "http://localhost:$HOST_PORT/api/v1/add?a=2&b=3"
                            exit 0
                        fi
                        sleep 5
                    done
                    echo "Deployment did not become healthy"
                    docker compose ps
                    docker compose logs --tail=50 calculator
                    exit 1
                '''
            }
        }
    }

    post {
        success {
            mail to: "${env.NOTIFY_EMAIL}",
                 subject: "SUCCESS: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: "Build succeeded and was deployed.\nImage: ${env.DOCKER_IMAGE}:${env.IMAGE_TAG}\nDetails: ${env.BUILD_URL}"
        }
        failure {
            mail to: "${env.NOTIFY_EMAIL}",
                 subject: "FAILURE: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                 body: "Build failed.\nDetails: ${env.BUILD_URL}console"
        }
    }
}
