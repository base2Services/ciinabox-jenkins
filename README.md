# ciinabox Jenkins

## How to test

### Requirements
 - Install the Docker Toolbox https://www.docker.com/docker-toolbox
 - Java 8 SDK

### Docker Setup
create a ciinabox docker machine

```sh
$ docker-machine create -d virtualbox --virtualbox-memory=2048 --virtualbox-cpu-count=2 ciinabox
```

 configure docker to use the ciinabox machine

```sh
$ eval "$(/usr/local/bin/docker-machine env ciinabox)"
```

Run ciinabox Jenkins test

```sh
$ docker-compose up -d
Creating ciinaboxjenkins_jenkins-docker-slave_1
Creating ciinaboxjenkins_jenkins_1
```

### Running the example ciinabox Jenkins configuration

Create a symlink to your ciinaboxes directory for example

```sh
$ ln -s ciinaboxes.example ciinaboxes
```

Ensure the example ciinabox jenkins url matches you

```sh
$ docker-machine ip ciinabox
192.168.99.100
$ cat ciinaboxes/example/jenkins/jobs.yml
```
```yaml
#ciinabox jenkins config

jenkins_url: http://192.168.99.100:8080/
....
```

From the ciinabox-jenkins directory run.

```sh
$ ./gradlew jenkins -Dciinabox=example -Dusername=ciinabox -Dpassword=ciinabox

:compileJava UP-TO-DATE
:compileGroovy UP-TO-DATE
:processResources UP-TO-DATE
:classes UP-TO-DATE
:jenkins

processing job: Example-Pull-Request
Processing provided DSL script
example - created
example/Example-Pull-Request - created

processing job: Example-Branch-With-Artifact-Archive
Processing provided DSL script
example - updated
example/Example-Branch-With-Artifact-Archive - created

processing job: Example-Branch-With-Copy-Artifact-No-SCM
Processing provided DSL script
example - updated
example/Example-Branch-With-Copy-Artifact-No-SCM - created

BUILD SUCCESSFUL

Total time: 53.205 secs

This build could be faster, please consider using the Gradle Daemon: http://gradle.org/docs/2.4/userguide/gradle_daemon.html
```

open http://192.168.99.100:8080/ in a browser and confirm the example job have been loaded correctly
