package com.base2.ciinabox

import com.base2.ciinabox.ext.ExtensionBase
import com.base2.util.ReflectionUtils

class JobHelper {

  static void defaults(def job, def vars) {
    description(job, vars.get('description',vars.get('jobName','')))
    labels(job, vars.get('labels', []))
    discardBuilds(job, vars)
    concurrentBuilds(job, vars)
    parameters(job,vars.get('parameters',[:]))
    cronTrigger(job,vars)
    scm(job,vars)
    copyLatestSuccessfulArtifacts(job, vars.get('artifacts',[]), vars.get('jobNames',[]))
    buildEnvironment(job, vars.get('build_environment', [:]), vars)
    steps(job,vars)
    triggerJobs(job,vars.get('trigger',[:]),vars)
    publishers(job, vars)
    postTriggerJobs(job,vars.get('post_trigger',[]),vars)
    archive(job, vars.get('archive', []))
    gitPush(job, vars.get('push',[]))


    ReflectionUtils.getTypesImplementingInterface(ExtensionBase,'com.base2.ciinabox.ext').
            each { Class<? extends ExtensionBase> clazz ->
      def extesnion = clazz.newInstance()
      extesnion.setJobConfiguration(vars)
      extesnion.extend(job)
    }
  }

  static void labels(def job, def labels) {
    labels.each {
      job.label(it)
    }
  }

  static void parameters(def job, def params) {
    params.each { param, value ->
      param = param.toUpperCase()
      //case1 - param is defined by default boolean value
      if(value instanceof Boolean ) {
        job.parameters {
          booleanParam(param,value,'')
        }
        return
      }

      //case2 - params is defined by array of choice options
      if(isCollectionOrArray(value)) {
        job.parameters {
          choiceParam(param,value,'')
        }
        return
      }

      //case3 - param is defined by default String value
      if(value instanceof String || value instanceof Number) {
        job.parameters {
          stringParam(param,value.toString(),'')
        }
        return
      }

      //case4 - param is defined by map, having default value, options and/or description
      if(value instanceof Map){
        job.parameters {
          def description = value['description']
          if (!description) {
            description = ''
          }
          if (value['options'] != null && isCollectionOrArray(value['options'])) {
            choiceParam(param, value['options'], description)
            return
          }
          def defaultValue = value['default']
          if (defaultValue == null) {
            defaultValue = ''
          }
          if (defaultValue instanceof Boolean) {
            booleanParam(param, defaultValue, description)
            return
          }

          //both String and Number values will be passed in as String Jenkins params
          stringParam(param, defaultValue.toString(), description)
        }
      }
    }
  }

  static void scm(def job, def vars) {
    if (vars.containsKey('github') && vars.get('github') instanceof List) {
      multipleGitSCM(job, vars.get('github'), vars)
    } else if (vars.containsKey('git')) {
      gitSCM(job, vars.get('git'), vars)
    } else if (vars.containsKey('branch')) {
      githubScm(job, vars.get('github', [ : ]), vars)
    } else if (vars.containsKey('repo')) {
      pullRequestGithub(job, vars.get('github', [ : ]), vars.get('repo'), vars)
    }
  }

  static void buildEnvironment(def job, def buildEnv, def vars) {
    def env = mergeWithDefaults(buildEnv, vars, 'build_environment')
    job.wrappers {
      if(env.containsKey('ssh_agent')) {
        sshAgent(env.get('ssh_agent'))
      }
      if(env.containsKey('environment')) {
        def environment = toUpperCaseKeys(env.get('environment', [:]))
        environmentVariables {
          envs(environment)
        }
      }
      if (env.containsKey('build_user_vars')) {
        if (env.get('build_user_vars')) {
          delegate.wrapperNodes << new NodeBuilder().'org.jenkinsci.plugins.builduser.BuildUser' {}
        }
      }
    }
  }

  static void steps(def job, def vars) {
    shellSteps(job, vars)
    dslSteps(job,vars)
    gradleSteps(job,vars)
  }

  static void shellSteps(def job, def vars) {
    def scriptDir = lookupDefault(vars,'scripts_dir','ciinaboxes')
    def shellSteps = vars.get('shell',[])
    shellSteps.each { step ->
      step.each { type, value ->
        String script = value
        if(type == 'file') {
          File file = new File(script)
          if(file.exists()) {
            script = file.text
          } else {
            script = job.jobManagement.readFileInWorkspace(scriptDir + "/" + script)
          }
        }
        job.steps {
          shell script
        }
      }
    }
  }

  static void dslSteps(def job, def vars) {
    def scriptDir = lookupDefault(vars,'scripts_dir','ciinaboxes')
    def dslStep = vars.get('dsl')
    if(dslStep != null) {
      def dslSource = dslStep.get('filter','')
      def dslFile = new File("${scriptDir}/${dslStep.get('file')}")
      if(dslFile.exists()) {
        dslSource = dslFile.text
      }
      def remove = dslStep.get('remove_action','DISABLE')
      def ignore = dslStep.get('ignore_existing',false)
      def classpath = dslStep.get('additional_classpath')
      job.steps {
        dsl {
          if(dslFile.exists()) {
            text(dslSource)
          } else {
            external(dslSource)
          }
          ignoreExisting(ignore)
          removeAction(remove)
          if(classpath != null) {
            additionalClasspath(classpath)
          }
        }
      }
    }
  }

  static void gradleSteps(def job, def vars) {
    def gradleStep = vars.get('gradle',null)
    if(gradleStep == null) {
      return
    }
    job.steps {
      gradle {
        gradleStep.each { type, value ->
          switch(type) {
            case 'build_file':
              buildFile(value)
            break
            case 'gradle_name':
              gradleName(value)
            break
            case 'root_build_script_dir':
              rootBuildScriptDir(value)
            break
            case 'use_wrapper':
              useWrapper(value)
              makeExecutable()
            break
            case 'tasks':
              value.each { t ->
                tasks(t)
              }
            break
            case 'switches':
              value.each { s ->
                switches(s)
              }
            break
          }
        }
      }
    }
  }

  static void gitSCM(def job, def source, def vars) {
    def block = mergeWithDefaults(source, vars, 'git')
    job.triggers {
        if(block.containsKey('cron')) {
            scm(block.get('cron'))
        }
    }
    job.scm {
      git {
        remote {
          url(block.get('url'))
          credentials(block.get('credentials'))
          name(block.get('name'))
          refspec(block.get('refspec'))
        }
        branch(block.get('branch'))

        extensions {
          wipeOutWorkspace()
          if(block.containsKey('repo_target_dir')) {
            relativeTargetDirectory(block.get('repo_target_dir'))
          }
        }

        configure {
            if(block.containsKey('excluded_users')) {
                it / 'extensions' / 'hudson.plugins.git.extensions.impl.UserExclusion' {
                    'excludedUsers'(block.get('excluded_users'))
                }
            }
        }
      }
    }
  }

  static void pullRequestGithub(def job, def scm, def repo, def vars) {
    def gh = mergeWithDefaults(scm, vars, 'github')
    job.scm {
      git {
        remote {
          credentials(gh.get('credentials'))
          github(repo, gh.get('protocol'), gh.get('host'))
          refspec('+refs/pull/*:refs/remotes/origin/pr/*')
        }
        branch('${sha1}')
        extensions {
          wipeOutWorkspace()
          if(gh.containsKey('repo_target_dir')) {
            relativeTargetDirectory(gh.get('repo_target_dir'))
          }
        }
      }
    }

    job.configure { Node node ->
      def hooks = gh.get('use_hooks')
      def orgs = gh.get('org_white_list').join(' ')
      def pollCron = gh.get('cron')
      if(pollCron == null){
        pollCron = '* * * * *'
      }
      if(node.get('triggers')==null){
        node.appendNode('triggers')
      }
      node / 'triggers' {
        'org.jenkinsci.plugins.ghprb.GhprbTrigger' {
            delegate.current.attributes = ['plugin':'ghprb']
            orgslist orgs
            cron pollCron
            spec pollCron
            useGitHubHooks hooks
            triggerPhrase gh.get(trigger_phrase,"ok to merge")
            permitAll true
            autoCloseFailedPullRequests false
            allowMembersOfWhitelistedOrgsAsAdmin true
        }
      }
    }
  }

  static void multipleGitSCM(def jobObject, def scmList, def jobConfiguration) {
    jobObject.multiscm {
      for (int i = 0; i < scmList.size(); i++) {
        def config = scmList[i]
        def block = mergeWithDefaults(config, jobConfiguration, 'github')

        git {
          remote {
            credentials(block.get('credentials'))
            github(config.repo, block.get('protocol'), block.get('host'))
          }
          branch(config.branch)
          extensions {
            wipeOutWorkspace()
            if (block.containsKey('repo_target_dir')) {
              relativeTargetDirectory(block.get('repo_target_dir'))
            }
          }
        }
      }
    }
    scmList.each { scm ->
      if (scm.containsKey('push') && scm.push) {
        jobObject.triggers {
          githubPush()
        }
      }
    }
  }

  static void githubScm(def job, def scm, def vars) {
    def block = mergeWithDefaults(scm, vars, 'github')
    def repo = vars.get('repo')
    def buildBranch = vars.get('branch')
    job.scm {
      git {
        remote {
          credentials(block.get('credentials'))
          github(repo, block.get('protocol'), block.get('host'))
        }
        branch(buildBranch)
        extensions {
          wipeOutWorkspace()
          if(block.containsKey('repo_target_dir')) {
            relativeTargetDirectory(block.get('repo_target_dir'))
          }
        }
      }
    }
    if(block.get('push',false)) {
      job.triggers{
        githubPush()
      }
    }
  }


  static void copyLatestSuccessfulArtifacts(def job, def artifacts, def jobNames = []) {
    artifacts.each { artifact ->
      job.steps {
        if(artifact.get('job').contains("*")) {
          def pattern = artifact.get('job').replaceAll(/\*/, "(.*)")
          def matchingJobs = matchJobName(pattern, jobNames)
          matchingJobs.each { jobName ->
            copyArtifacts(jobName) {
              includePatterns(artifact.get('file_pattern'))
              if(artifact.get('exclude_file_pattern')) {
                excludePatterns(artifact.get('exclude_file_pattern'))
              }
              if(artifact.get('optional')){
                optional()
              }
              targetDirectory(artifact.get('target_directory',''))
              flatten()
              fingerprintArtifacts()
              buildSelector {
                if (artifact.containsKey('build_number')) {
                  buildNumber(artifact.get('build_number'))
                } else {
                  latestSuccessful(true)
                }
              }
            }
          }
        } else {
          copyArtifacts(artifact.get('job')) {
            includePatterns(artifact.get('file_pattern'))
            if(artifact.get('exclude_file_pattern')) {
              excludePatterns(artifact.get('exclude_file_pattern'))
            }
            if(artifact.get('optional')){
              optional()
            }
            targetDirectory(artifact.get('target_directory',''))
            flatten()
            fingerprintArtifacts()
            buildSelector {
              if (artifact.containsKey('build_number')) {
                buildNumber(artifact.get('build_number'))
              } else {
                latestSuccessful(true)
              }
            }
          }
        }
      }
    }
  }

  static void triggerJobs(def job, def triggers, def vars) {
    triggers.each { triggerJob ->
      job.steps {
        downstreamParameterized {
          trigger(triggerJob.get('job')) {
            if(triggerJob.get('block',false)) {
              block {
                buildStepFailure('FAILURE')
                failure('FAILURE')
                unstable('UNSTABLE')
              }
            }
            parameters {
              if(triggerJob.get('current_parameters',false)) {
                currentBuild()
              }
              if(triggerJob.containsKey('parameters')) {
                predefinedProps(toUpperCaseKeys(triggerJob.get('parameters',[:])))
              }
              if(triggerJob.containsKey('properties_file')){
                def properties_file = triggerJob.get('properties_file')
                if(properties_file instanceof Map){
                  propertiesFile(properties_file['name'],properties_file['fail_on_missing'])
                }else if(properties_file instanceof String){
                  propertiesFile(properties_file)
                } else {
                  throw new RuntimeException("Properties file must be given as map of name/fail_on_missing keys or string with file name")
                }
              }
            }
          }
        }
      }
    }
  }

  static postTriggerJobs(def job, def triggers, def vars) {
    job.publishers {
      triggers.each { triggerJob ->
        downstreamParameterized {
          trigger(triggerJob.get('job')) {
            parameters {
              if(triggerJob.get('current_parameters',false)) {
                currentBuild()
              }
              if(triggerJob.containsKey('parameters')) {
                predefinedProps(toUpperCaseKeys(triggerJob.get('parameters',[:])))
              }
            }
          }
        }
      }
    }
  }

  static void archive(def job, def archives) {
    archives.each { archive ->
      job.publishers {
        archiveArtifacts(archive)
      }
    }
  }

  static void gitPush(def job, def branches) {
    if(branches.size() > 0) {
      job.publishers {
        git {
          pushOnlyIfSuccess()
          branches.each { brnch ->
            branch('origin', brnch)
          }
        }
      }
    }
  }

  static void publishers(def job, def vars) {
    def gh = lookupDefault(vars, 'github', jobDefaults())
    job.publishers {
      if(vars.get('branch') == null && vars.containsKey('repo')) {
        mergePullRequest {
          mergeComment(gh.get('merge_comment', 'merged by jenkins'))
          onlyAdminsMerge(true)
          failOnNonMerge(true)
        }
      }
      defaultPublishers(job, vars)
    }
  }

  static void defaultPublishers(def job, def vars) {
    def slackChannel = lookupDefault(vars, 'slack_channel')
    job.publishers {
      chucknorris()
    }
    if(slackChannel) {
      job.configure { node ->
            node / 'publishers' {
                'jenkins.plugins.slack.SlackNotifier' {
                  delegate.current.attributes = [plugin: 'slack']
                  room slackChannel
                  startNotification true
                  notifyFailure true
                  notifySuccess true
                  notifyAborted true
                  notifyBackToNormal true
                }
            }
      }
    }
  }

  static void description(def job, def desc) {
    job.description(desc)
  }

  static void discardBuilds(def job, def vars) {
    if (vars.containsKey('discardBuilds')) {
      def parameters = vars.get('discardBuilds')
      def builds = (parameters.containsKey('buildsToKeep') ? parameters.get('buildsToKeep') : -1 )
      def days = (parameters.containsKey('daysToKeep') ? parameters.get('daysToKeep') : -1 )
      def artifactBuilds = (parameters.containsKey('artifactBuildsToKeep') ? parameters.get('artifactBuildsToKeep') : -1 )
      def artifactDays = (parameters.containsKey('artifactDaysToKeep') ? parameters.get('artifactDaysToKeep') : -1 )
      job.logRotator {
        numToKeep(builds)
        daysToKeep(days)
        artifactNumToKeep(artifactBuilds)
        artifactDaysToKeep(artifactDays)
      }
    }
  }

  static void concurrentBuilds(def job, def vars) {
    if (vars.get('concurrentBuild',false)) {
      job.concurrentBuild()
    }
  }

  static void cronTrigger(def job, def vars) {
    if(vars.containsKey('cronTrigger')) {
      job.triggers {
        cron(vars.get('cronTrigger'))
      }
    }
  }

  private static boolean isCollectionOrArray(object) {
    [Collection, Object[]].any { it.isAssignableFrom(object.getClass()) }
  }

  private static lookupDefault(def vars, def key, def defaultValue = null) {
    def defaults = vars.get('defaults',[:])
    return defaults.get(key,defaultValue)
  }

  private static toUpperCaseKeys(def hash) {
    def upper = [:]
    hash.each { key, value ->
      upper.put(key.toUpperCase(),value)
    }
    return upper
  }

  private static jobDefaults(def key = null) {
    def defaults = [
      "github": [
        "credentials" : "github",
        "merge_comment" : "merged by jenkins",
        "trigger_phrase" : "ok to merge",
        "org_white_list": [],
        "cron" : null,
        "protocol": "https",
        "host": "github.com",
        "use_hooks" : true
      ],
      "git": [
        "credentials": "github",
        "branch": "master",
        "name": "",
        "refspec": ""
      ],
      "bitbucket": [
        "credentials": "bitbucket",
        "username": '${BITBUCKET_USER}',
        "password": '${BITBUCKET_PASSWORD}',
        "ci_identifier": "jenkins",
        "ci_name": "jenkins",
        "ci_skip_phrases": ""
      ],
      "build_environment":[:]
    ]
    return key == null ? defaults : defaults.get(key)
  }

  private static mergeWithDefaults(def block, def vars, def key) {
    def defaults =  lookupDefault(vars, key, jobDefaults(key))
    if(defaults instanceof Map) {
      defaults = combine( jobDefaults().get(key), defaults, block)
    }
    return defaults
  }

  private static combine( Map... sources ) {
    if (sources.length == 0) return [:]
    if (sources.length == 1) return sources[0]

    sources.inject([:]) { result, source ->
        source.each { k, v ->
            result[k] = result[k] instanceof Map ? combine(result[k], v) : v
        }
        result
    }
  }

  private static matchJobName(def pattern, def jobNames) {
    jobNames.findAll { it =~ pattern }
  }
}
