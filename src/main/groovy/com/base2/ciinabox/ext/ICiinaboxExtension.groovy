package com.base2.ciinabox.ext

import javaposse.jobdsl.dsl.Job

/**
 * Created by nikolatosic on 9/03/2017.
 */
public interface ICiinaboxExtension {

    void extend(Job job, def jobConfiguration)

}