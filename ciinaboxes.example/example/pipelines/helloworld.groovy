#!groovy
stage("HelloWorld"){

  node {
    sh "Hello World!!"

    sh "Value of key1 is ${env.KEY1}"
  }

}