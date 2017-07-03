package com.base2.rest

import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import javaposse.jobdsl.dsl.*
import groovy.json.*

class RestApiJobManagement extends MockJobManagement {

  final RESTClient restClient

  private __DIRTY_MARKERS = [
          'DONT UPDATE WITH CIINABOX',
          'DON\'T UPDATE WITH CIINABOX',
          'DO NOT UPDATE WITH CIINABOX',
          'SKIP CIINABOX UPDATE',
          'CIINABOX SKIP',
          'CIINABOX SKIP UPDATE',
  ]

  RestApiJobManagement(String baseUrl) {
    if (!baseUrl != null && !baseUrl.endsWith("/")) {
      baseUrl += "/"
    }
    println("using jenkins url: ${baseUrl}")
    restClient = new RESTClient(baseUrl)
    restClient.ignoreSSLIssues()
    restClient.handler.failure = {it}
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

  private String getJobDescription(xml){
    try {
      def xmlObj = new XmlSlurper().parseText(xml)
      return xmlObj.description
    }catch (Exception ex){
      println "Error reading description from remote xml"
      return ""
    }
  }

  boolean createOrUpdateConfig(String name, String xml, boolean ignoreExisting, boolean isView) throws NameNotProvidedException {
    boolean success
    String status

    String existingXml = fetchExistingXml(name, isView)
    def update = {
      success = update(name, xml, isView)
      status = success ? 'updated' : 'update failed'
    }
    if (existingXml) {
      //check if job description is any of dirty markers
      def jobDescription = getJobDescription(existingXml)
      if(__DIRTY_MARKERS.any{ jobDescription.toUpperCase().contains(it) }){
        def overwrite = Boolean.getBoolean('overrideDirtyJobs')

        if(!overwrite){
          success = true
          status = " skipped due dirty marker ${jobDescription} (as job description)"
          println("CIINABOX HINT: use -DoverrideDirtyJobs=true property to override job ${name}")
        } else {
          update()
        }
      }
      else if (ignoreExisting) {
        success = true
        status = 'ignored'
      }
      else {
        update()
      }
    }
    else {
      success = create(name, xml, isView)
      status = success ? 'created' : 'create failed'
    }

    println "$name - $status"
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

  private boolean create(String name, String xml, boolean isView) {
    String job
    String path
    if (name.contains('/')) {
      int index = name.lastIndexOf('/')
      String folder = name[0..(index - 1)]
      job = name[(index + 1)..-1]
      path = getPath(folder, isView) + '/createItem'
    }
    else {
      job = name
      path = isView ? 'createView' : 'createItem'
    }

    HttpResponseDecorator resp = restClient.post(
            path: path,
            body: xml,
            query: [name: job],
            requestContentType: 'application/xml'
    )

    resp.status == 200
  }

  private boolean update(String name, String xml, boolean isView) {
    HttpResponseDecorator resp = restClient.post(
            path: getPath(name, isView) + '/config.xml',
            body: xml,
            requestContentType: 'application/xml'
    )

    resp.status == 200
  }

  private String fetchExistingXml(String name, boolean isView) {
    HttpResponseDecorator resp = restClient.get(
            contentType: ContentType.TEXT,
            path: getPath(name, isView) + '/config.xml',
            headers: [Accept: 'application/xml'],
    )
    resp?.data?.text
  }

  static String getPath(String name, boolean isView) {
    if (name.startsWith('/')) {
      return '/' + getPath(name[1..-1], isView)
    }
    isView ? "view/$name" : "job/${name.replaceAll('/', '/job/')}"
  }
}
