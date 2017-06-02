package com.base2.ciinabox.ext

import com.base2.util.MapUtil
import javaposse.jobdsl.dsl.Job

/**
 * Base class for all ciinabox dsl extensions, implementing common logic
 * such as storing jobConfiguration as instance variable, extending configuration and defaults
 * lookup
 * Created by nikolatosic on 29/03/2017.
 */
public abstract class ExtensionBase implements ICiinaboxExtension {

  protected def jobConfiguration


  @Override
  void setJobConfiguration(def jobConfiguration) {
    this.jobConfiguration = jobConfiguration

    //pick up defaults section from job file
    if(this.jobConfiguration.containsKey('defaults')){
      MapUtil.extend(this.jobConfiguration, this.jobConfiguration.defaults)
    }
    this.jobConfiguration.remove('defaults')
  }

  @Override
  void extend(Job job) {
    if (this.jobConfiguration.get(getConfigurationKey())) {
      def extensionConfiguration = this.jobConfiguration[getConfigurationKey()]
      // allow simple forms for job attributes key: value
      if (extensionConfiguration.getClass().isPrimitive()) {
        extensionConfiguration = [ "${getDefaultConfigurationAttribute()}": extensionConfiguration ]
      }
      MapUtil.extend(extensionConfiguration, getDefaultConfiguration())
      extendDsl(job, extensionConfiguration, this.jobConfiguration)
    }
  }

  /**
   * If feature is defined in key: value form, rather than key:map form
   * this will automatically be expaneded into key: [ defaultConfigKey: value ]
   * @return
   */
  abstract String getDefaultConfigurationAttribute()

  /**
   * Key identifying extension
   * @return
   */
  abstract String getConfigurationKey()

  /**
   * Return default configuration for extension
   * @return
   */
  abstract Map getDefaultConfiguration()

  /**
   * Extend JobDsl job object
   * @param job
   * @param extensionConfiguration
   */
  abstract void extendDsl(Job job, def extensionConfiguration, def jobConfiguration)

  /**
   * Get default value from 'defaults' section of DSL file
   * @param key
   * @param defaultValue
   * @return
   */
  protected def defaultConfigValue(def key, def defaultValue) {
    return jobConfiguration['defaults'][key] ? jobConfiguration['defaults'][key] : defaultValue
  }

}
