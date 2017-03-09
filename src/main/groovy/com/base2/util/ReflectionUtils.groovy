package com.base2.util

import org.reflections.Reflections
import org.reflections.scanners.SubTypesScanner
import org.reflections.scanners.TypeAnnotationsScanner
import org.reflections.util.ClasspathHelper
import org.reflections.util.ConfigurationBuilder

/**
 * Created by nikolatosic on 9/03/2017.
 */
class ReflectionUtils {

  static {
    System.setProperty('org.slf4j.simpleLogger.defaultLogLevel','ERROR')
  }

  public static Set<Class> getTypesImplementingInterface(Class<?> interfaceClazz, String packageName) {
    Reflections reflections = new Reflections(
            new ConfigurationBuilder()
                    .setScanners(new SubTypesScanner(false), new TypeAnnotationsScanner())
                    .setUrls(ClasspathHelper.forPackage(packageName))
    );


    reflections.getSubTypesOf(interfaceClazz);
  }

}
