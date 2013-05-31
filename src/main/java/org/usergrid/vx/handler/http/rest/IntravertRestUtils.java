package org.usergrid.vx.handler.http.rest;

import org.apache.cassandra.db.ConsistencyLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vertx.java.core.http.HttpServerRequest;

/**
 * Utility class for extracting useful information from the request for the REST API handlers.
 * 
 * @author zznate
 * @author boneill42
 */
public class IntravertRestUtils {
  private static Logger log = LoggerFactory.getLogger(IntravertRestUtils.class);

  /**
   * Defines the Consistency level header: "X-Consistency-Level"
   */
  public static final String CONSISTENCY_LEVEL_HEADER = "X-Consistency-Level";

  public static final String KEYSPACE = "ks";
  public static final String COLUMN_FAMILY = "cf";
  public static final String ROWKEY = "rawKey";
  public static final String COLUMN = "col";

  /**
   * Returns the consistency level from the header if present. Defaults to
   * {@link ConsistencyLevel#ONE} if:
   * <ul>
   * <li>The consistency level header is not found</li>
   * <li>The consistency level header is found but {@link ConsistencyLevel#valueOf(String)} throws
   * an IllegalArgumentException (i.e. there was a typo)</li>
   * </ul>
   * 
   * See {@link #CONSISTENCY_LEVEL_HEADER} for the header definition
   * 
   * @param request
   * @return The level specified by the header or ONE according to the conditions defined above.
   */
  public static ConsistencyLevel fromHeader(HttpServerRequest request) {
    if (request.headers().contains(CONSISTENCY_LEVEL_HEADER)) {
      try {
        return ConsistencyLevel.valueOf(request.headers().get(CONSISTENCY_LEVEL_HEADER));
      } catch (IllegalArgumentException iae) {
        log.warn("Unable to deduce value for '{}' Header. Using default {} ",
                CONSISTENCY_LEVEL_HEADER, ConsistencyLevel.ONE.toString());
      }
    }
    // TODO this should be a configuration option one day
    return ConsistencyLevel.ONE;
  }

}
