package com.base2.rest

import groovyx.net.http.ContentType
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.RESTClient
import javaposse.jobdsl.dsl.*
import groovy.json.*

class RestApiPluginManagement {

  final RESTClient restClient

  RestApiPluginManagement(String baseUrl) {
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

  Map<String, Map> grabPluginMetadata() {
    def rval = [:]
    restClient.get(
            path: '/pluginManager/api/xml',
            contentType: ContentType.TEXT,
            headers: [Accept: 'application/xml'],
            query: [depth: 1, wrapper: 'plugins']
    ) {resp, xml ->

      if (resp.status != 200) {
        throw new RuntimeException("Unexpected HTTP status code while reading plugins:${resp.status}")
      }
      def xmlNode =  new XmlSlurper().parse(xml)
      // use short names as names
      xmlNode.plugin.each {
         def pluginData = [:],
            pluginShortName = it.shortName.text()
         it.children().each { c ->
            pluginData[c.name()] = c.text()
         }
         rval[pluginShortName] = pluginData
      } 
    }
    return rval
  }

}
