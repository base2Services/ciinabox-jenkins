package com.base2.ciinabox.ext

import com.base2.util.MapUtil
import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 9/03/2017.
 */
public class TriggeredByExtension implements ICiinaboxExtension {

  //available opts are 'SUCCESS', 'UNSTABLE' or 'FAILURE'.
  private static final defaults = [
          treshold: 'SUCCESS'
  ]

  @Override
  void extend(Job job, Object jobConfiguration) {
    if (jobConfiguration.get('triggeredBy')) {
      def triggerConfiguration = jobConfiguration.get('triggeredBy')

      MapUtil.extend(triggerConfiguration, defaults)

      job.triggers {
        upstream(triggerConfiguration.job, triggerConfiguration.treshold)
      }
    }
  }
}
