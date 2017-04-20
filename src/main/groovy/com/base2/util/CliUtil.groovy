package com.base2.util
import groovy.swing.SwingBuilder
/**
 * Created by nikolatosic on 5/26/17.
 */
class CliUtil {


    static proceedPrompt(message) {
        def answer = null

        while(true){
            println "$message (Y/N) ?"
            answer =  new Scanner(System.in).nextLine()
            if(['Y','N'].contains(answer)) break
        }

        if(answer.equals('N')){
            println "Exiting... "
            System.exit(-1)
        }
    }
}
