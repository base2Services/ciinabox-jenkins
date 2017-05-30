package com.base2.ciinabox.ext

import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 8/05/2017.
 */
class RemoteTriggerExtension extends ExtensionBase {

  @Override
  String getDefaultConfigurationAttribute() {
    'token'
  }

  @Override
  String getConfigurationKey() {
    'remoteTrigger'
  }

  @Override
  Map getDefaultConfiguration() {
    return [:]
  }

  @Override
  void extendDsl(Job job, Object extensionConfiguration, Object jobConfiguration) {
    job.configure {
      project ->
        (project / 'authToken').setValue(extensionConfiguration['token'])
    }
  }
}
