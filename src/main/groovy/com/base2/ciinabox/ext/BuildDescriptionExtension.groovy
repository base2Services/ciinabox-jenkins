package com.base2.ciinabox.ext

import com.base2.util.MapUtil
import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 9/03/2017.
 */
public class BuildDescriptionExtension implements ICiinaboxExtension {


  private static final defaults = [
    regularExpression: '',
    description: '',
    regularExpressionForFailed: '',
    descriptionForFailed: '',
    multiConfigurationBuild: false
  ]

  @Override
  void extend(Job job, Object jobConfiguration) {
    if (jobConfiguration.get('build_description')) {
      def descConfig = jobConfiguration.get('build_description')
      if(descConfig instanceof Map) {
          MapUtil.extend(descConfig, defaults)
      } else {
        def description = descConfig
        descConfig = [:]
        descConfig.put('description', description)
        MapUtil.extend(descConfig, defaults)
      }

      job.publishers {
        buildDescription(descConfig.regularExpression, descConfig.description, descConfig.regularExpressionForFailed, descConfig.descriptionForFailed, descConfig.multiConfigurationBuild)
      }
    }
  }
}
