package com.base2.ciinabox.ext

import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 9/03/2017.
 */
public class TriggeredByExtension extends ExtensionBase {

  public TriggeredByExtension(){}

  //available opts are 'SUCCESS', 'UNSTABLE' or 'FAILURE'.
  private static final defaults = [
          treshold: 'SUCCESS'
  ]

  @Override
  String getConfigurationKey() {
    return 'triggeredBy'
  }

  @Override
  Map getDefaultConfiguration() {
    return defaults
  }

  @Override
  void extendDsl(Job job, def extensionConfiguration, def jobConfiguration) {
    job.triggers {
      upstream(extensionConfiguration.job, extensionConfiguration.treshold)
    }
  }
}
