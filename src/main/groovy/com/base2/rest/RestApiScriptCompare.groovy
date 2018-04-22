package com.base2.rest

import difflib.DiffUtils
import difflib.Patch
import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import javaposse.jobdsl.dsl.*


class RestApiScriptCompare extends MockJobManagement {

  final RESTClient restClient
  final String jenkinsUrl

  RestApiScriptCompare(String baseUrl) {
    if (!baseUrl != null && !baseUrl.endsWith("/")) {
      baseUrl += "/"
    }
    println("using jenkins url: ${baseUrl}")
    restClient = new RESTClient(baseUrl)
    restClient.ignoreSSLIssues()
    restClient.handler.failure = { it }
    jenkinsUrl = baseUrl
  }

  void setCredentials(String username, String password) {
    restClient.headers['Authorization'] = 'Basic ' + "$username:$password".bytes.encodeBase64()
  }

  @Override
  String getConfig(String jobName) throws JobConfigurationNotFoundException {
    String xml = fetchExistingXml(jobName)
    if (!xml) {
      throw new JobConfigurationNotFoundException(jobName)
    }

    xml
  }

  @Override
  boolean createOrUpdateConfig(Item item, boolean ignoreExisting) throws NameNotProvidedException {
    createOrUpdateConfig(item.name, item.xml, ignoreExisting, false)
  }

  @Override
  void createOrUpdateView(String viewName, String config, boolean ignoreExisting) throws NameNotProvidedException, ConfigurationMissingException {
    createOrUpdateConfig(viewName, config, ignoreExisting, true)
  }

  boolean createOrUpdateConfig(String name, String xml, boolean ignoreExisting, boolean isView) throws NameNotProvidedException {
    boolean success
    String status

    String existingXml = fetchExistingXml(name, isView)
    if (existingXml) {

      def remoteObj = new XmlSlurper().parseText(existingXml),
          localObj = new XmlSlurper().parseText(xml),
          remoteScripts = [ ],
          localScripts = [ ]



      //populate script arrays
      remoteObj.children().each {
        if (it.name() == 'builders'){
          it.children().each { b ->
            if(b.name() == 'hudson.tasks.Shell'){
              remoteScripts << b.text()
            }
          }
        }
      }
      localObj.children().each {
        if (it.name() == 'builders'){
          it.children().each { b ->
            if(b.name() == 'hudson.tasks.Shell'){
              localScripts << b.text()
            }
          }
        }
      }
      def max = remoteScripts.size() > localScripts.size() ? remoteScripts.size() : localScripts.size()

      //compare
      for (int i = 0; i < max; i++) {
        if(remoteScripts.size() < i+1){
          println "Job ${name} :: Following script not found on remote machine:\n\n${localScripts[0]}"
          continue
        }
        if(localScripts.size() < i+1){
          println "Job ${name} :: Following remote script not found in DSL :\n\n${remoteScripts[0]}"
          continue
        }

        if(!remoteScripts[i].toString().equals(localScripts[i].toString())){
          println "Difference in script #${i} in DSL and on remote. \n\n"
          Patch patch = DiffUtils.diff(
                  Arrays.asList(localScripts[i].split("\n")),
                  Arrays.asList(remoteScripts[i].split("\n"))
          )
          println "------"
          patch.getDeltas().each {
            def sourceLine = it.original.position + 1,
                remoteLine = it.revised.position + 1,
                sourceText = String.join("\n",it.original.lines),
                remoteText = String.join("\n",it.revised.lines)

            println "Local  ${String.format("L#%4s", sourceLine)}:|\n $sourceText"
            println "Remote ${String.format("L#%4s", remoteLine)}:|\n $remoteText"
            println "------"
          }

        } else {
          println "Script #${i} identical on remote and local dsl "
        }
      }

    } else {
      println "Job ${name} does not exist on remote server, could not compare"
    }

    success
  }

  @Override
  InputStream streamFileInWorkspace(String filePath) throws IOException {
    new File(filePath).newInputStream()
  }

  @Override
  String readFileInWorkspace(String filePath) throws IOException {
    new File(filePath).text
  }


  private String fetchExistingXml(String name, boolean isView) {
    HttpResponseDecorator resp = restClient.get(
            contentType: ContentType.TEXT,
            path: getPath(name, isView) + '/config.xml',
            headers: [ Accept: 'application/xml' ],
    )
    if(!(resp?.data?.text) || resp.statusLine.statusCode != 200){
      def fullUrl = "${jenkinsUrl}${getPath(name, isView)}/config.xml"
      println "GET ${fullUrl}\nStatus: ${resp.statusLine}\nBody:${resp?.data}\n"
    }
    resp?.data?.text
  }

  static String getPath(String name, boolean isView) {
    if (name.startsWith('/')) {
      return '/' + getPath(name[1..-1], isView)
    }
    isView ? "view/$name" : "job/${name.replaceAll('/', '/job/')}"
  }
}
