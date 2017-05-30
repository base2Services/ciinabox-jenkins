package com.base2.util

import com.udojava.evalex.Expression;

class VersionUtil {

    public static final verifyVersionConstraint(version, constraint){
        def dotCount1 = version.toString().split('\\.').length,
            dotCount2 = constraint.limit.toString().split('\\.').length,
            vComponents1 = version.toString().split('\\.'),
            vComponents2 = constraint.limit.toString().split('\\.'),
            deg = dotCount1 > dotCount2 ? dotCount1 : dotCount2,
            v1 = 0.0, 
            v2 = 0.0

        //build out version numbers as floats
        for(def i = 0;i<deg;i++){
            v1 = v1 + (Math.pow(10,deg-i)) * (vComponents1.length > i ? new Double(vComponents1[i]) : 0)
            v2 = v2 + (Math.pow(10,deg-i)) * (vComponents2.length > i ? new Double(vComponents2[i]) : 0)
        }

        //evaluate expression
        return new Expression("${v1} ${constraint.op} ${v2}").eval().toString().equals('1')
    }
}