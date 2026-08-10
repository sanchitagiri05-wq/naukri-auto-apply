pipeline {
    agent any

    environment {
        JAVA_HOME = "/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home"
        PATH = "${JAVA_HOME}/bin:/opt/homebrew/bin:${env.PATH}"
    }

    stages {

        stage('Check Java') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean compile'
            }
        }

        stage('Test') {
            steps {
                sh 'mvn test'
            }
        }
    }
}
