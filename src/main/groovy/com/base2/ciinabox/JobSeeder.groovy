package com.base2.ciinabox

import javaposse.jobdsl.dsl.DslScriptLoader
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import com.base2.rest.RestApiJobManagement

String ciinabox = System.getProperty('ciinabox')
String ciinaboxes = System.getProperty('ciinaboxes','ciinaboxes')
String username = System.getProperty('username')
String password = System.getProperty('password') // password or token
String jobFileToProcess = System.getProperty('jobfile')
String jenkinsOverrideUrl = System.getProperty('url')

baseDir = new File(".").absolutePath
baseDir = baseDir.substring(0, baseDir.length()-2)
ciinaboxesDir = new File(ciinaboxes)

if (!ciinabox) {
    println 'usage: -Dciinabox=<ciinabox_name> [-Dciinaboxes=<ciinaboxes dir>] [-Durl=<jenkins_url>] [-Dusername=<username>] [-Dpassword=<password>] [-Djobfile=myjobs.yml]'
    System.exit 1
}

def yaml = new Yaml()
def processedJobs = false
new FileNameFinder().getFileNames("${ciinaboxesDir.absolutePath}/${ciinabox}/jenkins/", "*jobs.yml").each { String jobsFile ->
  if(jobFileToProcess == null || jobsFile.contains(jobFileToProcess)) {
    def jobs = (Map) yaml.load(new File(jobsFile).text)
    println "\nLoading jobs from file: $jobsFile"
    if(jenkinsOverrideUrl != null) {
      jobs['jenkins_url'] = jenkinsOverrideUrl
    }
    manageJobs(baseDir, username, password, jobs)
    processedJobs = true
  }
}
if(!processedJobs) {
  j = jobFileToProcess ?: 'jobs.yml'
  println "no ${j} file found for ${ciinabox} found in ${ciinaboxesDir.absolutePath}/jenkins"
}

def manageJobs(def baseDir, def username, def password, def jobs) {

  RestApiJobManagement jm = new RestApiJobManagement(jobs['jenkins_url'])
  if (username && password) {
      jm.setCredentials username, password
  }
  def jobNames = []
  jobs['jobs'].each { job ->
    jobNames << job.get('folder','') + '/' + job.get('name')
  }
  jobs['jobs'].each { job ->
    jm.parameters.clear()
    jm.parameters['baseDir'] = baseDir
    jm.parameters['jobBaseDir'] = "$baseDir/ciinabox-bootstrap/jenkins"
    jm.parameters['defaults'] = jobs['defaults']
    jobTemplate = new File("$baseDir/jobs/${job.get('type','default')}.groovy").text
    if(!job.containsKey('config')) {
      job.put('config',[:])
    }

    if(job.containsKey('type') && job.get('type') != 'default') {
      jobName = job.get("name","${job['repo'].split('/')[1]}-${job['type'].split('/')[1]}")
    } else if(job.containsKey('name')) {
      jobName = job.get('name')
    } else {
      throw new IllegalArgumentException('job requires either a type or a name')
    }
    println "\nprocessing job: $jobName"

    jm.parameters << job
    jm.parameters['jobName'] = jobName
    jm.parameters['jobNames'] = jobNames
    DslScriptLoader.runDslEngine(jobTemplate, jm)
  }
}
