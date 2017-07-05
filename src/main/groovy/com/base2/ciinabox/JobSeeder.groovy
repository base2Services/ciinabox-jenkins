package com.base2.ciinabox

import com.base2.util.*
import com.base2.rest.RestApiJobManagement
import com.base2.rest.RestApiPluginManagement
import javaposse.jobdsl.dsl.AbstractJobManagement
import javaposse.jobdsl.dsl.DslScriptLoader
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils
import org.yaml.snakeyaml.Yaml
import java.nio.file.Paths

String ciinabox = System.getProperty('ciinabox')
String ciinaboxes = System.getProperty('ciinaboxes', 'ciinaboxes')
String username = System.getProperty('username')
String password = System.getProperty('password') // password or token
String jobFileToProcess = System.getProperty('jobfile')
String jobToProcess = System.getProperty('job')
String jenkinsOverrideUrl = System.getProperty('url')


baseDir = new File(".").absolutePath
baseDir = baseDir.substring(0, baseDir.length() - 2)
ciinaboxesDir = new File(ciinaboxes)
ciinaboxDir  = new File("${ciinaboxesDir}/${ciinabox}")

//validate existance of directories
[ciinaboxesDir,ciinaboxDir].each { dir ->
  if (!(dir.exists() && dir.isDirectory())) {
    System.err.println "\n${dir} is not directory!!!\n"
    System.exit(-1)
  }
}

if (!ciinabox) {
  println 'usage: -Dciinabox=<ciinabox_name> [-Dciinaboxes=<ciinaboxes dir>] [-Durl=<jenkins_url>] [-Dusername=<username>] [-Dpassword=<password>] [-Djobfile=myjobs.yml]'
  System.exit 1
}

def yaml = new Yaml()
def processedJobs = false

def allYamlFileNames = new FileNameFinder().getFileNames("${ciinaboxesDir.absolutePath}/${ciinabox}/jenkins/", "*.yml")

allYamlFileNames.each {String jobsFile ->

  def matchingByJobfile =
          jobFileToProcess != null &&
                  (jobFileToProcess.equalsIgnoreCase(Paths.get(jobsFile).fileName.toString())
                          || jobFileToProcess.equalsIgnoreCase(FilenameUtils.removeExtension(Paths.get(jobsFile).fileName.toString())))


  if (jobFileToProcess == null || matchingByJobfile) {
    def jobs
    try {
      jobs = (Map) yaml.load(new File(jobsFile).text)
    } catch (Exception ex) {
      System.err.println("[ERROR] Error loading YAML file ${jobsFile}:")
      System.err.println(ex.toString())
      System.exit(-2)
    }

    if (jenkinsOverrideUrl != null) {
      jobs['jenkins_url'] = jenkinsOverrideUrl
    }

    //if specific job is defined
    if (jobs['jobs'] && StringUtils.isNotEmpty(jobToProcess)) {
      jobs['jobs'] = jobs['jobs'].findAll {
        (it['name'] == null) || (it['name'].equalsIgnoreCase(jobToProcess))
      }
      if(jobs['jobs'].size() == 0){
        return
      }
    }
    println "\nLoading jobs from file: $jobsFile"
    checkPluginVersions([jenkins_url: jobs.jenkins_url, username:username, password: password])
    manageJobs(baseDir, username, password, jobs)
    processedJobs = true
  }
}
if (!processedJobs) {
  j = jobFileToProcess ?: 'jobs.yml'
  println "no ${j} file found for ${ciinabox} found in ${ciinaboxesDir.absolutePath}/jenkins"
}

def checkPluginVersions(config){
  String ignoreVersionConstraints = System.getProperty('ignore-version-constraints')
  def bIgnoreVersionConstraints = (ignoreVersionConstraints != null && ignoreVersionConstraints == "true")
  RestApiPluginManagement pluginManager = new RestApiPluginManagement(config['jenkins_url'])
  pluginManager.setCredentials(config.username, config.password)

  def pluginData = pluginManager.grabPluginMetadata(),
      pluginLimitsLoc = this.class.getClassLoader().getResource('plugin_limits.yml').getFile()
  pluginLimits =  new Yaml().load(new File(pluginLimitsLoc).text)

  pluginLimits.each { plugin,limits ->
    if (pluginData.containsKey(plugin)){
      def actualVersion = pluginData[plugin].version
      limits.each { limit ->
        println "Validating plugin ${plugin} version ${actualVersion} against constraint ${limit.op} ${limit.limit} ... "
        if(VersionUtil.verifyVersionConstraint(actualVersion,limit)){
          println " [SUCCESS] "
        } else {
          println " [ FAILURE ]\n Plugin ${plugin} version failed to satisfy constraint ${limit.op} ${limit.limit}"
          if(!bIgnoreVersionConstraints) {
            println "\n use -Dignore-version-constraints=true to ignore plugin version constraints\n"
            System.exit(-1)
          } else {
            println " Failed plugin dependency ignored via (ignore-version-constraints=true)"
          }
        }
      }
    } else {
      println "Plugin ${plugin} not found on remote server, skipping constraint validation"
    }
  }
}


def manageJobs(def baseDir, def username, def password, def objJobFile) {

  AbstractJobManagement jm
  String remoteUrl = objJobFile['jenkins_url']
  //override job management clazz if instructed so
  if(StringUtils.isBlank(System.getProperty('jobManagementClass'))) {
     jm = new RestApiJobManagement(remoteUrl)
  } else {
     jm = Class.forName(System.getProperty('jobManagementClass')).newInstance(remoteUrl)
  }

  if (username && password) {
    [jm].each { it.setCredentials(username, password)}
  }



  def jobNames = []
  objJobFile['jobs'].each { job ->
    jobNames << job.get('folder', '') + '/' + job.get('name')
  }

  objJobFile['jobs'].each {job ->
    jm.parameters.clear()
    jm.parameters['baseDir'] = baseDir
    jm.parameters['jobBaseDir'] = "$baseDir/ciinabox-bootstrap/jenkins"
    if (objJobFile['defaults']) {
      jm.parameters['defaults'] = objJobFile['defaults']
    }
    job.each { k, v -> if (k == 'pipeline') job['type'] = 'pipeline' }
    jobTemplate = new File("$baseDir/jobs/${job.get('type', 'default')}.groovy").text
    if (!job.containsKey('config')) {
      job.put('config', [:])
    }

    if (job.containsKey('type') && job.get('type') != 'default' && (!job.containsKey('name'))) {
      jobName = job.get("name", "${job['repo'].split('/')[1]}-${job['type'].split('/')[1]}")
    }
    else if (job.containsKey('name')) {
      jobName = job.get('name')
    }
    else {
      throw new IllegalArgumentException('job requires either a type or a name')
    }

    if (!job['folder']) {
      job['folder'] = ''
    }

    println "\nprocessing job: ${job['folder']}/${jobName}"

    jm.parameters << job
    jm.parameters['jobName'] = jobName
    jm.parameters['jobNames'] = jobNames

    def dslScriptLoader = new DslScriptLoader(jm)
    dslScriptLoader.runScript(jobTemplate)

  }
}
