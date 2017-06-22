package com.base2.ciinabox.ext

import javaposse.jobdsl.dsl.Job
import org.apache.commons.lang.StringUtils

/**
 * Created by nikolatosic on 5/26/17.
 */
class BitbucketExtension extends ExtensionBase {


  @Override
  String getDefaultConfigurationAttribute() {
    'repo'
  }

  @Override
  String getConfigurationKey() {
    'bitbucket'
  }

  @Override
  Map getDefaultConfiguration() {
    [
            pr_cron               : '* * * * *',
            ciKey                 : 'jenkins',
            ciName                : 'Jenkins',
            approveIfSuccess      : true,
            cancelOutdatedJobs    : false,
            checkDestinationCommit: false,
            commentTrigger        : 'test this please',
            push                  : false
    ]
  }

  @Override
  void extendDsl(Job job, Object extensionConfiguration, Object jobConfiguration) {
    //detect whether is regular or pr job
    if(extensionConfiguration.repo == null){
      println "No bitbucket repo defined for ${jobConfiguration.name} - " +
              "Probably bitbucket defaults defined but no bitbucket definition for job itself"
      return
    }

    if(extensionConfiguration instanceof List){
      handleBitbucketMultipleScm(job, extensionConfiguration)
      return
    }

    def hasBranch = extensionConfiguration.containsKey('branch') && StringUtils.isNotBlank(extensionConfiguration.branch),
        repoParts = extensionConfiguration.repo.split('/')
    if (repoParts.size() != 2) {
      throw new RuntimeException("Error configuring ${jobConfiguration.name}:" +
              "\nBitbucket repo must be in \$owner/\$repo format")
    }
    def repoOwner = repoParts[0], repo = repoParts[1]

    def protocol = (extensionConfiguration.protocol == 'ssh' ? 'git@bitbucket.org:' : 'https://bitbucket.org/')
    //configure scm
    job.scm {
      git {
        remote {
          credentials(extensionConfiguration.credentials)
          url("${protocol}${extensionConfiguration.repo}.git")
        }
        extensions {
          wipeOutWorkspace()
          if (extensionConfiguration.containsKey('repo_target_dir')) {
            relativeTargetDirectory(extensionConfiguration.get('repo_target_dir'))
          }
        }
        branch(hasBranch ? extensionConfiguration.branch : "*/\${sourceBranch}")
      }
    }

    //set cron for building upon commit
    if (extensionConfiguration.containsKey('cron')) {
      job.triggers {
        scm(extensionConfiguration.cron)
      }
    }

    //set bitbucket push event
    if(extensionConfiguration.push){
      job.triggers{
        bitbucketPush()
      }
    }

    //if branch not defined
    if (!hasBranch) {
      job.configure { Node node ->
        //add triggers XML node if it does not exist
        if(node.get('triggers')==null){
          node.appendNode('triggers')
        }

        node / 'triggers' {
          'bitbucketpullrequestbuilder.bitbucketpullrequestbuilder.BitbucketBuildTrigger' {
            delegate.current.attributes = [ 'plugin': 'bitbucket-pullrequest-builder' ]
            "spec" extensionConfiguration.pr_cron
            cron extensionConfiguration.pr_cron
            credentialsId extensionConfiguration.credentials
            ciKey extensionConfiguration.ciKey
            ciName extensionConfiguration.ciName
            approveIfSuccess extensionConfiguration.approveIfSuccess
            repositoryOwner  "${repoOwner}"
            repositoryName "${repo}"
            cancelOutdatedJobs extensionConfiguration.cancelOutdatedJobs
            checkDestinationCommit extensionConfiguration.checkDestinationCommit
            commentTrigger extensionConfiguration.commentTrigger
          }
        }
      }
    }
  }

  private handleBitbucketMultipleScm(Job job, extensionConfiguration) {
    job.multiscm {
      for (int i = 0; i < extensionConfiguration.size(); i++) {
        def config = extensionConfiguration[i]
        def protocol = (config.protocol == 'ssh' ? 'git@bitbucket.org:' : 'https://bitbucket.org/')
        def hasBranch = config.containsKey('branch') && StringUtils.isNotBlank(config.branch)
        git {
          remote {
            credentials(config.credentials)
            url("${protocol}${config.repo}.git")
          }
          extensions {
            wipeOutWorkspace()
            if (config.containsKey('repo_target_dir')) {
              relativeTargetDirectory(config.get('repo_target_dir'))
            }
          }
          branch(hasBranch ? config.branch : "*/\${sourceBranch}")
        }
      }
    }
  }
}
