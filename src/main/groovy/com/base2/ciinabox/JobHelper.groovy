package com.base2.ciinabox

import javaposse.jobdsl.dsl.Job

class JobHelper {

  static void defaults(def job, def vars) {
    description(job, vars.get('description',vars.get('jobName','')))
    parameters(job,vars.get('parameters',[:]))
    scm(job,vars)
    copyLatestSuccessfulArtifacts(job, vars.get('artifacts',[]))
    steps(job,vars)
    triggerJobs(job,vars.get('trigger',[:]),vars)
    publishers(job, vars)
    archive(job, vars.get('archive', []))
    gitPush(job, vars.get('push',[]))
  }

  static void parameters(def job, def params) {
    params.each { param, value ->
      if(value instanceof Boolean ) {
        job.parameters {
          booleanParam(param.toUpperCase(),value,'')
        }
      } else if(isCollectionOrArray(value)) {
        job.parameters {
          choiceParam(param.toUpperCase(),value,'')
        }
      } else {
        job.parameters {
          stringParam(param.toUpperCase(),value,'')
        }
      }
    }
  }

  static void scm(def job, def vars) {
    if(vars.containsKey('branch')) {
      github(job, vars)
    } else if(vars.containsKey('repo')){
      pullRequestScm(job, vars.get('repo'), vars)
    }
  }

  static void steps(def job, def vars) {
    shellSteps(job, vars)
    dslSteps(job,vars)
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
    def dslStep = vars.get('dsl')
    if(dslStep != null) {
      def dslSource = dslStep.get('filter','')
      def remove = dslStep.get('remove_action','DISABLE')
      def ignore = dslStep.get('ignore_existing',false)
      def classpath = dslStep.get('additional_classpath')
      job.steps {
        dsl {
          external(dslSource)
          ignoreExisting(ignore)
          removeAction(remove)
          if(classpath != null) {
            additionalClasspath(classpath)
          }
        }
      }
    }
  }

  static void pullRequestScm(def job, def repo, def vars) {
    def gh = lookupDefault(vars, 'github', githubDefaults())
    job.scm {
      git {
        remote {
          credentials(gh.get('credentials','github'))
          github(repo)
          refspec('+refs/pull/*:refs/remotes/origin/pr/*')
        }
        branch('${sha1}')
      }
    }
    job.triggers {
      def orgs = gh.get('org_white_list')
      def pollCron = gh.get('cron')
      def pr = pullRequest {
        orgWhitelist(orgs)
        cron(pollCron)
        useGitHubHooks()
        triggerPhrase(gh.get('trigger_phrase',"ok to merge"))
        permitAll()
        autoCloseFailedPullRequests(false)
        allowMembersOfWhitelistedOrgsAsAdmin()
      }
    }
  }

  static void github(def job, def vars) {
    def gh = lookupDefault(vars, 'github', githubDefaults())
    def repo = vars.get('repo')
    def buildBranch = vars.get('branch')
    job.scm {
      git {
        remote {
          credentials(gh.get('credentials','github'))
          github(repo)
        }
        branch(buildBranch)
        wipeOutWorkspace()
      }
    }
  }

  static void copyLatestSuccessfulArtifacts(def job, def artifacts) {
    artifacts.each { artifact ->
      job.steps {
        copyArtifacts(artifact.get('job')) {
          includePatterns(artifact.get('file_pattern'))
          targetDirectory(artifact.get('target_directory',''))
          flatten()
          fingerprintArtifacts()
          buildSelector {
            latestSuccessful(true)
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
              if(triggerJob.get('curent_parameters',false)) {
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
    job.publishers {
      git {
        pushOnlyIfSuccess()
        branches.each { brnch ->
          branch('origin', brnch)
        }
      }
    }
  }

  static void publishers(def job, def vars) {
    def gh = lookupDefault(vars, 'github', githubDefaults())
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
    def slackChannel = lookupDefault(vars, 'slack_channel', 'devops')
    job.publishers {
      chucknorris()
      slackNotifications {
        projectChannel(slackChannel)
        notifyBuildStart()
        notifySuccess()
        notifyFailure()
        notifyBackToNormal()
      }
    }
  }

  static void description(def job, def desc) {
    job.description(desc)
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

  private static githubDefaults() {
    return [
      "credentials" : "github",
      "merge_comment" : "merged by jenkins",
      "trigger_phrase" : "ok to merge",
      "org_white_list": [],
      "cron" : null
    ]
  }
}
