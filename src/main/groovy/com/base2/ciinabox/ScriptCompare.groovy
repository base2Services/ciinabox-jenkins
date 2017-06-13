package com.base2.ciinabox

System.setProperty('jobManagementClass','com.base2.rest.RestApiScriptCompare')
def clazz = Class.forName('com.base2.ciinabox.JobSeeder')
(clazz.newInstance() as Script).run()

