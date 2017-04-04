package com.base2.ciinabox

import com.base2.rest.RestApiJobManagement
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

if (!ciinabox) {
  println 'usage: -Dciinabox=<ciinabox_name> [-Dciinaboxes=<ciinaboxes dir>] [-Durl=<jenkins_url>] [-Dusername=<username>] [-Dpassword=<password>] [-Djobfile=myjobs.yml]'
  System.exit 1
}

def yaml = new Yaml()
def processedJobs = false
new FileNameFinder().getFileNames("${ciinaboxesDir.absolutePath}/${ciinabox}/jenkins/", "*.yml").each {String jobsFile ->

  def matchingByJobfile =
          jobFileToProcess != null &&
                  (jobFileToProcess.equalsIgnoreCase(Paths.get(jobsFile).fileName.toString())
                          || jobFileToProcess.equalsIgnoreCase(FilenameUtils.removeExtension(Paths.get(jobsFile).fileName.toString())))


  if (jobFileToProcess == null || matchingByJobfile) {
    def jobs = (Map) yaml.load(new File(jobsFile).text)
    println "\nLoading jobs from file: $jobsFile"
    if (jenkinsOverrideUrl != null) {
      jobs['jenkins_url'] = jenkinsOverrideUrl
    }

    //if specific job is defined
    if (jobs['jobs'] && StringUtils.isNotEmpty(jobToProcess)) {
      jobs['jobs'] = jobs['jobs'].findAll {
        (it['name'] == null) || (it['name'].equalsIgnoreCase(jobToProcess))
      }
    }

    manageJobs(baseDir, username, password, jobs)
    processedJobs = true
  }
}
if (!processedJobs) {
  j = jobFileToProcess ?: 'jobs.yml'
  println "no ${j} file found for ${ciinabox} found in ${ciinaboxesDir.absolutePath}/jenkins"
}

def manageJobs(def baseDir, def username, def password, def objJobFile) {

  RestApiJobManagement jm = new RestApiJobManagement(objJobFile['jenkins_url'])
  if (username && password) {
    jm.setCredentials username, password
  }
  def jobNames = []
  objJobFile['jobs'].each {job ->
    jobNames << job.get('folder', '') + '/' + job.get('name')
  }
  objJobFile['jobs'].each {job ->
    jm.parameters.clear()
    jm.parameters['baseDir'] = baseDir
    jm.parameters['jobBaseDir'] = "$baseDir/ciinabox-bootstrap/jenkins"
    if (objJobFile['defaults']) {
      jm.parameters['defaults'] = objJobFile['defaults']
    }
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

    println "\nprocessing job: $jobName"

    jm.parameters << job
    jm.parameters['jobName'] = jobName
    jm.parameters['jobNames'] = jobNames

    def dslScriptLoader = new DslScriptLoader(jm)
    dslScriptLoader.runScript(jobTemplate)

  }
}
