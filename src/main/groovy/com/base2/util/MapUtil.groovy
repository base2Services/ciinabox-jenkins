package com.base2.util

/**
 * Created by nikolatosic on 9/03/2017.
 */
final class MapUtil {

  /**
   * Extends groovy map1 with values from map2. If key already exists in map1 it is NOT being overwritten
   * @param map1
   * @param map2
   * @return
   */
  public static final extend(def map1, def map2) {
    map2.each {k, v ->
      if (!map1[k]) {
        map1[k] = v
      }
    }

  }


}
