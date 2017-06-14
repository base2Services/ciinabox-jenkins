# ciinabox Jenkins

## Jenkins plugin constraints

Any features of these CLI depending on Jenkins plugin, or plugin version
should be defined in `src/main/resources/plugin_limits.yml`

`./gradlew jenkins` task will fail by default if constraints are not
satisfied. To ignore failed cnstraint checks
use `-Dignore-version-constraints=true` option when running gradle task

## Comparing local scripts with remote ones 

` ./gradlew jobsScriptCompare ` task will download all scripts specified
as execute script step on configured jenkins jobs. Report on script diff is printed to standard output.
`jobsScriptCompare` task accepts same switches as `jenkins` task

`-Dusername=jenkinsuser`

`-Djob=jobToPublishOrCompare`

`-Djobfile=jobFileToPublishOrCompare`

`-Durl=http://override.jenkins.url`

`-Dignore-version-constraints=true`

output example:

```

processing job: Backup-EBS-Production
Processing provided DSL script
Script #0 identical on remote and local dsl 
Script #1 identical on remote and local dsl 


processing job: Backup-RDS-Production
Processing provided DSL script
Difference in script #0 in DSL and on remote. 


------
Local  L#  36:|
 #   TODO: check whatever we are in backup window for DB instance. If so, automated backup to copy from may not be available and user should be notified
Remote L#  36:|
 
------

```


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

# Job DSL Reference

Ciinabox Jenkins jobs are defined in yaml files. To specify yaml being used, use `-Djobfile=<filename>` switch. By default all jobs matching following path are used:

`$CIINABOXES/$CIINABOX/jenkins/*.jobs.yml`

Alternatively you can specify single job from yaml file using
``-Djob=$jobname` switch. All examples from this reference file can be found in `ciinaboxes.example/example/jenkins/dsl-reference-jobs.yml` file.

## Job Definition

### Job name & Job Folder
Only property required to define job is `name` property. E.G:

```
jenkins_url: http://localhost:8080/

jobs:
 - name: MyJobName

 - name: MyJobName2
   folder: MyFolder
```

Configuration above will create two jobs, respectively named MyJobName and MyJobName2 within 'MyFolder'

### Description
If you want to add description to job, you can do so via 'description' property. If not, job name is used as job description.

```
jenkins_url: http://localhost:8080/

jobs:
 - name: MyJobWithDescription
   folder: MyFolder
   description: My Job Description
```

### Build Parameters

To specify build parameters use `parameters` property. Parameters element is map of parameter definitions. Map key is used as parameter name, upper cased,
while for map values there are two options
- Default value. If true or false are specified, parameter shall be provisioned as boolean parameter, presented as checkbox on Jenkins UI
- List of values. 
- Map defining parameter. `default`, `options`, `description` are available keys for the map. If dropdown select is desired as parameter input,
`options` key should be used, providing list of available options. `default` defines default value, while `description` will give pretty info 
on parameter role and usage to Jenkins end user 

```
jenkins_url: http://localhost:8080/

jobs:
 - name: MyJobWithParameters
   folder: dsl-doc
   parameters:
     param1: value1                                        # default value is 'value1'
     param2: ''                                            # no default value
     deployment: true                                      # Boolean parameters have true / false value
     deploymentEnvironment:                                # Choice parameter
       - dev
       - stage
       - prod

 - name: MyJobWithParametersDefinedAsMap
   folder: dsl-doc
   parameters:                                            # Define parameters as map of maps
     param1:                                              # Parameter name will be uppercased
       default: true                                      # Default value
       description: 'Deploy artifact?'                    # Parameter description

     param2:
       description: 'If no default value provided, empty string shall be used'

     choice_param:
       options:                                           # Define options for choice parameter
        - option1
        - option2
        - option3
       description: 'Use dropdown form to select value for CHOICE_PARAM'
```

### Labels

To restrict on which node specific job can be executed, use `labels` job property

```
jenkins_url: http://localhost:8080/

jobs:
 - name: MyLabeledJob
   labels:
     - MavenBuild
```

### Discarding old builds

You can define rotation of job data, either by giving number of days to keep builds (artifacts) or number of builds (artifacts) to keep

```
jenkins_url: http://localhost:8080/

jobs:
 - name: JobWith10BuildsKept
   folder: RotationExample
   discardBuilds:
     buildsToKeep: 10        # Store latest 10 builds

 - name: JobWith10ArtifactsKept
   folder: RotationExample
   discardBuilds:
     artifactBuildsToKeep: 10   #Store latest 10 artifacts

 - name: JobWithBuildsKept10Days
   folder: RotationExample
   discardBuilds:
     daysToKeep: 10           # Store builds for 10 days

 - name: JobWithArtifactsKept10Days
   folder: RotationExample
   discardBuilds:
     artifactDaysToKeep: 10           # Store artifacts for 10 days
```

### Parallel builds

To allow multiple builds of your job running in parallel, use `concurrentBuild` job property

```
jenkins_url: http://localhost:8080/

jobs:
 - name: ParallelJob
   concurrentBuild: true
```

## Copy artifacts from other jobs

```yaml


```

## Source Code Repository

### Git SCM

In order to use any Git repository accessible via Jenkins as code repo, `git` property is available

```
jenkins_url: http://localhost:8080/

jobs:
 - name: GenericGitBuild
   git:
     credentials: myGitCreds                                # Credentials with 'myGitCreds' are required in Jenkins credentials store
     url: git@github.com:myOrg/myApp.git                    # Github is used only as an example, can by any git repo
     branch: feature/new-hot-feature                        # Branch name
     repo_target_dir: appcode                               # Checkout in workspace sub-directory

 - name: GenericGitBuildWithRefspec
   git:
     url: https://github.com/nodejs/readable-stream         # Public repo - credentials property is not required
     branch: tags/v2.0.4                                    # Build specific tag
```

### Github Repository

Ciinabox Jenkins utility makes it easy to work with Github:

```
jenkins_url: http://localhost:8080/

# Defaults section applies to all jobs being provisioned in single run
defaults:
  github:
    protocol: ssh                                           # SSH or HTTP
    credentials: my-gh-creds                                # Not required for public repos, this should
                                                            # be ID of Jenkins credentials
                                                            # https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin
jobs:
 - name: CiinaboxGithub
   folder: dsl-doc
   repo: base2Services/ciinabox                             # GitHub repo, with owner
   branch: master                                           # Branch to build
   
   
```

Depending on type of protocol being used, you may want to specify
either a private key or username/password key when creating credentials (`my-gh-creds`) in example above

### Github Pull Request Builder

If you want your project build on opened PR on Github, just omit `branch` part in above's configuration
and your job will be triggered upon pull request with Github Pull Request Builder plugin
All of regular commands(comments) on PR should work if your Jenkins installation is setup properly for GitHub
web hooks. More info can be found on [plugin's page](https://github.com/jenkinsci/ghprb-plugin)

```
jenkins_url: http://localhost:8080/

# Defaults section applies to all jobs being provisioned in single run
defaults:
  github:
    protocol: ssh                                           # SSH or HTTP
    credentials: my-gh-creds                                # Not required for public repos, this should
                                                            # be ID of Jenkins credentials
                                                            # https://wiki.jenkins-ci.org/display/JENKINS/Credentials+Plugin

jobs:
 - name: CiinaboxGithub-PullRequest
   folder: dsl-doc
   repo: base2Services/ciinabox                             # GitHub repo, with owner
```

### Bitbucket Repository

For triggering bitbucket builds, you can either poll the SCM, or 
start the build via webhook. Both approaches can be seen in example
below

```
jenkins_url: http://localhost:8080/

 - name: BitbucketJob
   folder: dsl-doc
   bitbucket:
     push: true                                             # Trigger build upon BB push
     cron: "* * * * *"                                      # Poll SCM for changes
     repo: nlstevenen/java-experimenting-with-java-8-features # BB repo to pull sources from
     branch: master                                         # Which branch to build if omitted pull request builder is configured
     repo_target_dir: app_code                              # Checkout in workspace sub-folder
     credentials: my-bb-creds                               # Credentials to use for authorization with BitBucket
     pr_cron : "* * * * *"                                  # Pull request builder, defaults to every minute
     commentTrigger : "test this please"                    # Pull request builder re-test comment, defaults to 'retest this please'
     ciKey          : "jenkins"                             # CI Key for pull request builder, defaults to 'jenkins'
     ciName         : "Jenkins"                             # CI name for pull request builder, defaults to 'Jenkins'
     approveIfSuccess: true                                 # for pull request builder, defaults to true
     cancelOutdatedJobs: false                              # for pull request builder, defaults to false
     checkDestinationCommit: false                          # for pull request builder, defaults to false
     
```


### Build Triggers

### Bitbucket pull request builder

If you want job to build bitbucket pull requests, just configure `bitbucket` block
without `branch` key

### Cron

To trigger builds using cron expression, use `cron` property

```
job: MyFolder/MyCronJob
cronTrigger: */5 * * * *   ## Runs every 5 minutes
```

### Build Environment


### Build Steps
