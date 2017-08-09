package com.base2.ciinabox.ext

import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 8/9/17.
 */
class JunitReportPublisher extends ExtensionBase {

  @Override
  String getDefaultConfigurationAttribute() {
    'path'
  }

  @Override
  String getConfigurationKey() {
    'junit'
  }

  @Override
  Map getDefaultConfiguration() {
    [ 'healthScaleFactor': 1.0 ]
  }

  @Override
  void extendDsl(Job job, Object extensionConfiguration, Object jobConfiguration) {
    job.publishers {
      archiveJunit(extensionConfiguration.path) {
        healthScaleFactor(extensionConfiguration.healthScaleFactor)
      }
    }
  }
}
