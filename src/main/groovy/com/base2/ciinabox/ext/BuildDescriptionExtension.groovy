package com.base2.ciinabox.ext

import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 9/03/2017.
 */
public class BuildDescriptionExtension extends ExtensionBase {


  private static final defaults = [
          regularExpression         : '',
          description               : '',
          regularExpressionForFailed: '',
          descriptionForFailed      : '',
          multiConfigurationBuild   : false
  ]

  @Override
  String getDefaultConfigurationAttribute() {
    'description'
  }

  @Override
  String getConfigurationKey() {
    'build_description'
  }

  @Override
  Map getDefaultConfiguration() {
    defaults
  }

  @Override
  void extendDsl(Job job, Object extensionConfiguration, Object jobConfiguration) {
    def descConfig = extensionConfiguration
    job.publishers {
      buildDescription(descConfig.regularExpression,
              descConfig.description,
              descConfig.regularExpressionForFailed,
              descConfig.descriptionForFailed,
              descConfig.multiConfigurationBuild)
    }
  }
}
