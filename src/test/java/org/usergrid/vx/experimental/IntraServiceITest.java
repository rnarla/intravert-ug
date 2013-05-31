/* 
 *   Copyright 2013 Nate McCall and Edward Capriolo
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package org.usergrid.vx.experimental;

import static junit.framework.Assert.assertNotNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.cassandra.cql3.QueryProcessor;
import org.apache.cassandra.db.ConsistencyLevel;
import org.apache.cassandra.db.marshal.AbstractCompositeType;
import org.apache.cassandra.db.marshal.AbstractType;
import org.apache.cassandra.db.marshal.CompositeType;
import org.apache.cassandra.db.marshal.Int32Type;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.cassandra.thrift.Column;
import org.apache.cassandra.thrift.CqlResult;
import org.apache.cassandra.thrift.ThriftClientState;
import org.apache.cassandra.transport.messages.ResultMessage;
import org.apache.cassandra.utils.ByteBufferUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.usergrid.vx.client.IntraClient2;
import org.vertx.java.core.Vertx;

import com.hazelcast.util.Base64;


@RunWith(CassandraRunner.class)
@RequiresKeyspace(ksName = "myks")
@RequiresColumnFamily(ksName = "myks", cfName = "mycf")
@SuppressWarnings({ "rawtypes", "unchecked" })
public class IntraServiceITest {

  private Logger logger = LoggerFactory.getLogger(IntraServiceITest.class);

  

  @DataLoader(dataset = "mydata.txt")
  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void atest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("myks")); // 0
    req.add(Operations.setColumnFamilyOp("mycf")); // 1
    req.add(Operations.setAutotimestampOp(true)); // 2
    req.add(Operations.setOp("rowa", "col1", "7")); // 3
    req.add(Operations.sliceOp("rowa", "col1", "z", 4)); // 4
    req.add(Operations.getOp("rowa", "col1")); // 5
    // create a rowkey "rowb" with a column "col2" and a value of the result of operation 7
    req.add(Operations.setOp("rowb", "col2", Operations.ref(5, "value"))); // 6
    // Read this row back
    req.add(Operations.getOp("rowb", "col2"));// 7

    req.add(Operations.consistencyOp("ALL")); // 8
    req.add(Operations.listKeyspacesOp()); // 9
    // req.add(Operations.listColumnFamilyOp("myks"));//10

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);

    System.out.println(res);

    Assert.assertEquals("OK", res.getOpsRes().get("0"));
    Assert.assertEquals("OK", res.getOpsRes().get("1"));
    Assert.assertEquals("OK", res.getOpsRes().get("2"));
    Assert.assertEquals("OK", res.getOpsRes().get("3"));
    List<Map> x = (List<Map>) res.getOpsRes().get("4");
    Assert.assertEquals("Y29sMQ==", x.get(0).get("name"));
    Assert.assertEquals("Nw==", x.get(0).get("value"));

    x = (List<Map>) res.getOpsRes().get("5");
    Assert.assertEquals("Nw==", x.get(0).get("value"));

    Assert.assertEquals("OK", res.getOpsRes().get("6"));

    x = (List<Map>) res.getOpsRes().get("7");
    Assert.assertEquals("Tnc9PQ==", x.get(0).get("value"));
    // TODO theseused ot be byte buffers...now theyare encoded strings...what happened

    Assert.assertEquals("OK", res.getOpsRes().get("8"));
    Assert.assertEquals(true, ((List<String>) res.getOpsRes().get("9")).contains("myks"));

  }

  @Test
  // Tis test now hangs.
  public void exceptionHandleTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.createKsOp("makeksagain", 1)); // 0
    req.add(Operations.createKsOp("makeksagain", 1)); // 1
    req.add(Operations.createKsOp("makeksagain", 1)); // 2
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    Assert.assertEquals("OK", res.getOpsRes().get("0"));
    Assert.assertEquals(1, res.getOpsRes().size());
    Assert.assertNotNull(res.getException());
    Assert.assertEquals(new Integer(1), res.getExceptionId());
  }


  @Test
  public void processorTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("procks")); // 0
    req.add(Operations.createKsOp("procks", 1)); // 1
    req.add(Operations.createCfOp("proccf")); // 2
    req.add(Operations.setColumnFamilyOp("proccf")); // 3
    req.add(Operations.setAutotimestampOp(true)); // 4
    req.add(Operations.assumeOp("procks", "proccf", "value", "UTF8Type"));// 5
    req.add(Operations.setOp("rowa", "col1", "wow")); // 6
    req.add(Operations.getOp("rowa", "col1")); // 7
    req.add(Operations.createProcessorOp("capitalize", "groovyclassloader",
            "public class Capitalize implements org.usergrid.vx.experimental.processor.Processor { \n"
                    + "  public List<Map> process(List<Map> input){"
                    + "    List<Map> results = new ArrayList<HashMap>();"
                    + "    for (Map row: input){" + "      Map newRow = new HashMap(); "
                    + "      newRow.put(\"value\",row.get(\"value\").toString().toUpperCase());"
                    + "      results.add(newRow); " + "    } \n" + "    return results;" + "  }"
                    + "}\n"));// 8
    // TAKE THE RESULT OF STEP 7 AND APPLY THE PROCESSOR TO IT
    req.add(Operations.processOp("capitalize", new HashMap(), 7));// 9
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    System.out.println(res);
    List<Map> x = (List<Map>) res.getOpsRes().get("7");
    Assert.assertEquals("wow", x.get(0).get("value"));
    System.out.println(res.getException());
    Assert.assertNull(res.getException());
    x = (List<Map>) res.getOpsRes().get("9");
    Assert.assertEquals("WOW", x.get(0).get("value"));
  }

  @Test
  public void intTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("intks")); // 0
    req.add(Operations.createKsOp("intks", 1)); // 1
    req.add(Operations.createCfOp("intcf")); // 2
    req.add(Operations.setColumnFamilyOp("intcf")); // 3
    req.add(Operations.setAutotimestampOp(true)); // 4
    req.add(Operations.assumeOp("intks", "intcf", "value", "UTF8Type"));// 5
    req.add(Operations.assumeOp("intks", "intcf", "column", "Int32Type"));// 6
    req.add(Operations.setOp("rowa", 1, "wow")); // 7
    req.add(Operations.getOp("rowa", 1)); // 8

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    List<Map> x = (List<Map>) res.getOpsRes().get("8");
    System.out.println(res);
    Assert.assertEquals("wow", x.get(0).get("value"));
    Assert.assertEquals(1, x.get(0).get("name"));
  }

  @Test
  public void ttlTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("ttlks")); // 0
    req.add(Operations.createKsOp("ttlks", 1)); // 1
    req.add(Operations.createCfOp("ttlcf")); // 2
    req.add(Operations.setColumnFamilyOp("ttlcf")); // 3
    req.add(Operations.setAutotimestampOp(true)); // 4
    req.add(Operations.assumeOp("ttlks", "ttlcf", "value", "UTF8Type"));// 5
    req.add(Operations.assumeOp("ttlks", "ttlcf", "column", "Int32Type"));// 6
    req.add(Operations.setOp("rowa", 1, "wow")); // 7
    req.add(Operations.setOp("rowa", 2, "wow").set("ttl", 1)); // 8
    // req.add( Operations.sliceOp("rowa", 1, 5, 4) ); //9
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    Assert.assertEquals("OK", res.getOpsRes().get("8"));

    Thread.sleep(2000);
    IntraReq r = new IntraReq();
    r.add(Operations.setKeyspaceOp("ttlks")); // 0
    r.add(Operations.setColumnFamilyOp("ttlcf")); // 1
    r.add(Operations.assumeOp("ttlks", "ttlcf", "value", "UTF8Type"));// 2
    r.add(Operations.assumeOp("ttlks", "ttlcf", "column", "Int32Type"));// 3
    r.add(Operations.sliceOp("rowa", 1, 5, 4)); // 4
    IntraRes rs = ic2.sendBlocking(r);
    List<Map> x = (List<Map>) rs.getOpsRes().get("4");
    Assert.assertEquals(1, x.size());

  }

  
  @Test
  public void CqlTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("cqlks")); // 0
    req.add(Operations.createKsOp("cqlks", 1)); // 1
    req.add(Operations.createCfOp("cqlcf")); // 2
    req.add(Operations.setColumnFamilyOp("cqlcf")); // 3
    req.add(Operations.setAutotimestampOp(true)); // 4
    req.add(Operations.assumeOp("cqlks", "cqlcf", "value", "Int32Type"));// 5
    req.add(Operations.assumeOp("cqlks", "cqlcf", "column", "Int32Type"));// 6
    req.add(Operations.setOp("rowa", 1, 2)); // 7
    req.add(Operations.getOp("rowa", 1)); // 8
    req.add(Operations.cqlQuery("select * from cqlcf", "3.0.0", false)); // 9

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    System.out.println(res);
    List<Map> x = (List<Map>) res.getOpsRes().get("8");

    Assert.assertEquals(1, x.get(0).get("name"));
    Assert.assertEquals(2, x.get(0).get("value"));
    x = (List<Map>) res.getOpsRes().get("9");
    String value = (String) x.get(2).get("value");
    ByteBuffer bytes = ByteBuffer.wrap(Base64.decode(value.getBytes()));
    // Assert.assertEquals( 2, ((ByteBuffer)x.get(2).get("value")).getInt() );
    Assert.assertEquals(new Integer(2), Int32Type.instance.compose(bytes));
  }

  @Test
  public void CqlNoResultTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("system"));
    req.add(Operations
            .cqlQuery(
                    "CREATE KEYSPACE test WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}",
                    "3.0.0"));// 0
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    Assert.assertEquals(2, res.getOpsRes().size());
  }

  @Test
  @Ignore
  public void clearTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("clearks")); // 0
    req.add(Operations.createKsOp("clearks", 1)); // 1
    req.add(Operations.createCfOp("clearcf")); // 2
    req.add(Operations.setColumnFamilyOp("clearcf")); // 3
    req.add(Operations.setAutotimestampOp(true)); // 4
    req.add(Operations.assumeOp("clearks", "clearcf", "value", "UTF8Type")); // 5
    req.add(Operations.setOp("rowa", 1, "wow")); // 6
    req.add(Operations.getOp("rowa", 1)); // 7
    req.add(Operations.getOp("rowa", 1)); // 8
    req.add(Operations.clear(8)); // 9

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);

    List<Map> x = (List<Map>) res.getOpsRes().get(7);
    Assert.assertEquals("wow", x.get(0).get("value"));

    x = (List<Map>) res.getOpsRes().get(8);
    Assert.assertEquals(0, x.size());
  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void cqlEngineTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("myks")); // 0
    req.add(Operations.setColumnFamilyOp("mycf")); // 1
    req.add(Operations.setAutotimestampOp(true)); // 2
    req.add(Operations.setOp("rowa", "col1", "7")); // 3

    IntraClient2 ic = new IntraClient2("localhost", 8080);
    @SuppressWarnings("unused")
    IntraRes res = ic.sendBlocking(req);

    ThriftClientState tcs = new ThriftClientState();
    tcs.setKeyspace("myks");
    ResultMessage rm = QueryProcessor.process("select * from mycf", ConsistencyLevel.ONE,
            tcs.getQueryState());
    CqlResult cr = rm.toThriftResult();
    List<Column> cols = cr.getRows().get(0).getColumns();
    ByteBufferUtil.string(cols.get(0).bufferForName());
    ByteBufferUtil.string(cols.get(1).bufferForName());
    ByteBufferUtil.string(cols.get(2).bufferForName());
    ByteBufferUtil.string(cols.get(0).bufferForValue());
    ByteBufferUtil.string(cols.get(1).bufferForValue());
    ByteBufferUtil.string(cols.get(2).bufferForValue());
    assertNotNull(rm);
  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void multiProcessTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("myks")); // 0
    req.add(Operations.setColumnFamilyOp("mycf")); // 1
    req.add(Operations.setAutotimestampOp(true)); // 2
    req.add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type")); // 3
    req.add(Operations.setOp("rowzz", "col1", "7")); // 4
    req.add(Operations.setOp("rowzz", "col2", "8")); // 5
    req.add(Operations.setOp("rowyy", "col4", "9")); // 6
    req.add(Operations.setOp("rowyy", "col2", "7")); // 7
    req.add(Operations.sliceOp("rowzz", "a", "z", 100));// 8
    req.add(Operations.sliceOp("rowyy", "a", "z", 100));// 9

    req.add(Operations
            .createMultiProcess(
                    "union",
                    "groovyclassloader",
                    "public class Union implements org.usergrid.vx.experimental.multiprocessor.MultiProcessor { \n"
                            + "  public List<Map> multiProcess(Map<Integer,Object> results, Map params){ \n"
                            + "    java.util.HashMap s = new java.util.HashMap(); \n"
                            + "    List<Integer> ids = (List<Integer>) params.get(\"steps\");\n"
                            + "    for (Integer id: ids) { \n"
                            + "      List<Map> rows = results.get(id+\"\"); \n"
                            + "      for (Map row: rows){ \n"
                            + "        s.put(row.get(\"value\"),\"\"); \n" + "      } \n"
                            + "    } \n" + "    List<HashMap> ret = new ArrayList<HashMap>(); \n"
                            + "    ret.add(s) \n" + "    return ret; \n" + "  } \n" + "} \n")); // 10
    Map paramsMap = new HashMap();
    List<Integer> steps = new ArrayList<Integer>();
    steps.add(8);
    steps.add(9);
    paramsMap.put("steps", steps);
    req.add(Operations.multiProcess("union", paramsMap)); // 11

    IntraClient2 ic = new IntraClient2("localhost", 8080);
    IntraRes res = ic.sendBlocking(req);

    List<Map> x = (List<Map>) res.getOpsRes().get("11");

    Set<String> expectedResults = new HashSet<String>();
    expectedResults.addAll(Arrays.asList(new String[] { "7", "8", "9" }));
    System.out.println(res);
    Assert.assertEquals(expectedResults, x.get(0).keySet());

  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void batchSetTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("myks")); // 0
    req.add(Operations.setColumnFamilyOp("mycf")); // 1
    req.add(Operations.setAutotimestampOp(true)); // 2
    req.add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type")); // 3
    req.add(Operations.assumeOp("myks", "mycf", "column", "UTF8Type")); // 4
    Map row1 = new HashMap();
    row1.put("rowkey", "batchkeya");
    row1.put("name", "col1");
    row1.put("value", "val1");

    Map row2 = new HashMap();
    row2.put("rowkey", "batchkeya");
    row2.put("name", "col2");
    row2.put("value", "val2");

    List<Map> rows = new ArrayList<Map>();
    rows.add(row1);
    rows.add(row2);
    req.add(Operations.batchSetOp(rows));// 5
    req.add(Operations.sliceOp("batchkeya", "a", "z", 100));// 6

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    List<Map> x = (List<Map>) res.getOpsRes().get("6");
    Assert.assertEquals(2, x.size());
    Assert.assertEquals("val1", x.get(0).get("value"));
    Assert.assertEquals("val2", x.get(1).get("value"));

  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void serviceProcessorTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.createServiceProcess("buildMySecondary", "groovy",
            "import org.usergrid.vx.experimental.* \n"
                    + "public class MyBuilder extends TwoExBuilder { \n" + "   \n" + "} \n")); // 0
    Map reqObj = new HashMap();
    reqObj.put("userid", "bsmith");
    reqObj.put("fname", "bob");
    reqObj.put("lname", "smith");
    reqObj.put("city", "NYC");

    req.add(Operations.setKeyspaceOp("myks"));// 1
    req.add(Operations.setAutotimestampOp(true));// 2
    req.add(Operations.createCfOp("users"));// 3
    req.add(Operations.createCfOp("usersbycity"));// 4
    req.add(Operations.createCfOp("usersbylast"));// 5
    req.add(Operations.serviceProcess("buildMySecondary", reqObj));// 6
    req.add(Operations.setColumnFamilyOp("usersbycity")); // 7
    req.add(Operations.assumeOp("myks", "usersbycity", "column", "UTF8Type"));
    req.add(Operations.sliceOp("NYC", "a", "z", 5)); // 9
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    System.out.println(res);
    List<Map> r = (List<Map>) res.getOpsRes().get("9");
    Assert.assertEquals("bsmith", r.get(0).get("name"));
  }

  @Test
  @Ignore
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void saveStateTest() throws Exception {
    // IntraClient ic = new IntraClient();
    // ic.setPayload("json");
    IntraReq r = new IntraReq();
    r.add(Operations.setKeyspaceOp("myks"));// 0
    r.add(Operations.setColumnFamilyOp("mycf")); // 1
    r.add(Operations.setAutotimestampOp(true)); // 2
    r.add(Operations.setOp("a", "b", "c")); // 3
    r.add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type"));// 4
    r.add(Operations.saveState());// 5

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(r);

    // IntraRes res = ic.sendBlocking(r);
    Assert.assertEquals("OK", res.getOpsRes().get(3));
    int id = (Integer) res.getOpsRes().get(5);

    IntraReq r2 = new IntraReq();
    r2.add(Operations.restoreState(id));// 0
    r2.add(Operations.setOp("d", "e", "f"));// 1
    r2.add(Operations.getOp("d", "e")); // 2
    IntraClient2 ic22 = new IntraClient2("localhost", 8080);
    IntraRes res2 = ic22.sendBlocking(r2);
    Assert.assertEquals("f", ((List<Map>) res2.getOpsRes().get(2)).get(0).get("value"));
  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void loadTestClient() throws Exception {
    final int ops = 1;
    long start = System.currentTimeMillis();

    Thread t = new Thread() {
      IntraClient2 ic = new IntraClient2("localhost", 8080);

      public void run() {
        // ic.setPayload("json");
        for (int i = 0; i < ops; ++i) {
          IntraReq req = new IntraReq();
          req.add(Operations.setKeyspaceOp("myks")); // 0
          req.add(Operations.setColumnFamilyOp("mycf")); // 1
          req.add(Operations.setAutotimestampOp(true)); // 2
          req.add(Operations.setOp("rowzz", "col1", "7")); // 4
          try {
            ic.sendBlocking(req);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    };
    t.start();
    t.join();
    long end = System.currentTimeMillis();
  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void optioanKSandCSTest() throws Exception {
    IntraReq req = new IntraReq();

    req.add(Operations.setAutotimestampOp(true)); // 0
    req.add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type"));// 1
    req.add(Operations.assumeOp("myks", "mycf", "column", "Int32Type"));// 2
    IntraOp setOp = Operations.setOp("optional", 1, "wow"); // 3
    setOp.set("keyspace", "myks");
    setOp.set("columnfamily", "mycf");
    req.add(setOp);
    // opa sexyy builder style
    req.add(Operations.getOp("optional", 1).set("keyspace", "myks").set("columnfamily", "mycf")); // 4
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    List<Map> x = (List<Map>) res.getOpsRes().get("4");

    Assert.assertEquals("wow", x.get(0).get("value"));
    Assert.assertEquals(1, x.get(0).get("name"));
  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void componentTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setAutotimestampOp(true)); // 0
    req.add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type"));// 1
    req.add(Operations.assumeOp("myks", "mycf", "column", "Int32Type"));// 2
    IntraOp setOp = Operations.setOp("optional", 1, "wow"); // 3
    setOp.set("keyspace", "myks");
    setOp.set("columnfamily", "mycf");
    req.add(setOp);
    Set<String> wanted = new HashSet<String>();
    wanted.addAll(Arrays.asList(new String[] { "value", "timestamp" }));
    req.add(Operations.componentSelect(wanted)); // 4
    // opa sexyy builder style
    req.add(Operations.getOp("optional", 1).set("keyspace", "myks").set("columnfamily", "mycf")); // 5
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    List<Map> x = (List<Map>) res.getOpsRes().get("5");

    Assert.assertEquals("wow", x.get(0).get("value"));
    Assert.assertEquals(true, x.get(0).containsKey("timestamp"));
    Assert.assertTrue((Long) x.get(0).get("timestamp") > 0);
    Assert.assertTrue(x.get(0).get("name") == null);
  }

  @Ignore
  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void cql3Schema() throws Exception {
    IntraReq req = new IntraReq();
    String ks = "cqltesting";
    req.add(Operations.setKeyspaceOp("system"));// 0
    req.add(Operations.createKsOp(ks, 1));// 1
    req.add(Operations.setKeyspaceOp(ks)); // 2
    String videos = "CREATE TABLE videos ( " + " videoid varchar, " + " videoname varchar, "
            + " username varchar, " + " description int, " + " tags varchar, "
            + " PRIMARY KEY (videoid,videoname) " + " ); ";
    req.add(Operations.cqlQuery(videos, "3.0.0", false));// 3
    // String query =
    // "SELECT columnfamily_name, comparator, default_validator, key_validator FROM system.schema_columnfamilies WHERE keyspace_name='%s'";
    // String formatted = String.format(query, ks);
    // req.add( Operations.setKeyspaceOp("system"));//4
    // req.add( Operations.cqlQuery(formatted, "3.0.0").set("convert", "")); //5
    String videoIns = "INSERT INTO videos (videoid,videoname,tags) VALUES (1,'weekend','fun games')";
    String videoIns1 = "INSERT INTO videos (videoid,videoname,tags) VALUES (2,'weekend2','fun games returns')";
    req.add(Operations.cqlQuery(videoIns, "3.0.0"));
    req.add(Operations.cqlQuery(videoIns1, "3.0.0"));
    req.add(Operations.cqlQuery("select * from videos WHERE videoid=2", "3.0.0", false).set(
            "convert", true));
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    System.out.println(res.getException());
    List<Map> results = (List<Map>) res.getOpsRes().get(6);
    Assert.assertEquals("videoid", results.get(0).get("name"));
    Assert.assertEquals("2", results.get(0).get("value"));

  }

  @Test
  public void timeoutOpTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setKeyspaceOp("timeoutks")); // 0
    req.add(Operations.createKsOp("timeoutks", 1)); // 1
    req.add(Operations.createCfOp("timeoutcf")); // 2
    req.add(Operations.setColumnFamilyOp("timeoutcf")); // 3
    req.add(Operations.setAutotimestampOp(true)); // 4
    req.add(Operations.assumeOp("timeoutks", "timeoutcf", "value", "UTF8Type"));// 5
    req.add(Operations.setOp("rowa", "col1", "20")); // 6
    req.add(Operations.setOp("rowa", "col2", "22")); // 7
    req.add(Operations.createFilterOp("ALongOne", "groovy", "{ row -> Thread.sleep(5000) }")); // 8
    req.add(Operations.filterModeOp("ALongOne", true)); // 9
    req.add(Operations.sliceOp("rowa", "col1", "col3", 10).set("timeout", 3000)); // 10
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    Assert.assertNotNull(res.getException());
    Assert.assertEquals(new Integer(10), res.getExceptionId());
  }

  @Test
  public void batchAcrossKeyspaces() throws Exception {
    List<Map> batch = new ArrayList<Map>();
    Map row = new HashMap();
    row.put("keyspace", "ks10");
    row.put("columnfamily", "cf10");
    row.put("rowkey", "mykey");
    row.put("name", "mycol");
    row.put("value", "myvalue");
    batch.add(row);
    row = new HashMap();
    row.put("keyspace", "ks11");
    row.put("columnfamily", "cf11");
    row.put("rowkey", "mykey2");
    row.put("name", "mycol2");
    row.put("value", "myvalue2");
    batch.add(row);

    IntraReq req = new IntraReq();
    req.add(Operations.setAutotimestampOp(true))
            .add(Operations.createKsOp("ks10", 1))
            .add(Operations.setKeyspaceOp("ks10"))
            .add(Operations.createCfOp("cf10"))
            .add(Operations.createKsOp("ks11", 1))
            .add(Operations.setKeyspaceOp("ks11"))
            .add(Operations.createCfOp("cf11"))
            .add(Operations.batchSetOp(batch))
            .add(Operations.assumeOp("ks10", "cf10", "value", "UTF8Type"))
            .add(Operations.assumeOp("ks11", "cf11", "value", "UTF8Type"))
            .add(Operations.getOp("mykey", "mycol").set("keyspace", "ks10")
                    .set("columnfamily", "cf10"))
            .add(Operations.getOp("mykey2", "mycol2").set("keyspace", "ks11")
                    .set("columnfamily", "cf11"));

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    System.out.println(res.getException());
    System.out.println(res.getExceptionId());
    List<Map> x = (List<Map>) res.getOpsRes().get("10");
    System.out.println(res);
    Assert.assertEquals("myvalue", x.get(0).get("value"));

    x = (List<Map>) res.getOpsRes().get("11");
    Assert.assertEquals("myvalue2", x.get(0).get("value"));

  }

  @Test
  @Ignore
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void preparedStatementTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.setAutotimestampOp(true)).add(
            Operations.setOp("preparedrow1", "preparedcol1", "preparedvalue1")
                    .set("keyspace", "myks").set("columnfamily", "mycf"));
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    Assert.assertEquals("OK", res.getOpsRes().get(1));

    IntraReq r2 = new IntraReq();
    r2.add(Operations.prepare()); // must be the first op
    r2.add(Operations.getOp(Operations.bindMarker(1), "preparedcol1").set("keyspace", "myks")
            .set("columnfamily", "mycf"));
    IntraRes res2 = ic2.sendBlocking(req);
    // Assert.assertEquals(0, res2.getOpsRes().get(0));
    Assert.assertEquals(1, res2.getOpsRes().size());
    Integer preparedId = (Integer) res2.getOpsRes().get(0);

    IntraReq req3 = new IntraReq();
    Map m = new HashMap();
    m.put(new Integer(1), "preparedrow1");
    req3.add(Operations.executePrepared(preparedId, m));
    IntraRes res3 = ic2.sendBlocking(req);
    List<Map> x = (List<Map>) res3.getOpsRes().get(0);
    Assert.assertEquals("preparedvalue1", ByteBufferUtil.string((ByteBuffer) x.get(0).get("value")));

  }

  @Test
  @Ignore
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void scannerTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.createScanFilter("peoplefromny", "groovy",
            "import org.usergrid.vx.experimental.* \n"
                    + "import org.usergrid.vx.experimental.scan.* \n"
                    + "public class MyScanner extends PeopleFromNY { \n"
                    + " public MyScanner() { super(); } \n" + "} \n"))
            .add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type"))
            .add(Operations.assumeOp("myks", "mycf", "column", "UTF8Type"))
            .add(Operations.setKeyspaceOp("myks")).add(Operations.setColumnFamilyOp("mycf"))
            .add(Operations.setOp("scannerrow", "ed", "NY")) // 3
            .add(Operations.setOp("scannerrow", "bob", "NY"))// 4
            .add(Operations.setOp("scannerrow", "pete", "FL"))// 5
            .add(Operations.setOp("scannerrow", "john", "TX"))// 6
            .add(Operations.setOp("scannerrow", "sara", "??"))// 7
            .add(Operations.setOp("scannerrow", "stacey", "NY"))// 8
            .add(Operations.setOp("scannerrow", "paul", "YO"))// 9
            .add(Operations.setOp("scannerrow2", "ed", "NY")) // 10
            .add(Operations.setOp("scannerrow2", "bob", "NY"))// 11
            .add(Operations.setOp("scannerrow2", "pete", "FL"))// 12
            .add(Operations.setOp("scannerrow2", "john", "TX"))// 13
            .add(Operations.setOp("scannerrow2", "sara", "??"))// 14
            .add(Operations.setOp("scannerrow2", "stacey", "NY"))// 15
            .add(Operations.setOp("scannerrow2", "paul", "YO"));// 16

    IntraClient2 ic = new IntraClient2("localhost", 8080);
    IntraRes res = ic.sendBlocking(req);
    Assert.assertEquals(null, res.getException());
  }

  @Test
  @Ignore
  @RequiresColumnFamily(ksName = "myks", cfName = "mycf")
  public void sliceNamesTest() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.assumeOp("myks", "mycf", "value", "UTF8Type"))
            .add(Operations.assumeOp("myks", "mycf", "column", "UTF8Type"))
            .add(Operations.setKeyspaceOp("myks"))
            .add(Operations.setColumnFamilyOp("mycf"))
            .add(Operations.setOp("slicename", "ed", "NY"))
            // 4
            .add(Operations.setOp("slicename", "bob", "NY"))
            // 5
            .add(Operations.setOp("slicename", "pete", "FL"))
            // 6
            .add(Operations.sliceByNames("slicename", Arrays.asList(new Object[] { "ed", "pete" })));
    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    List<Map> x = (List<Map>) res.getOpsRes().get(7);
    Assert.assertEquals("ed", x.get(0).get("name"));
    Assert.assertEquals("FL", x.get(1).get("value"));

  }

  @Test
  @RequiresColumnFamily(ksName = "myks", cfName = "mycountercf", isCounter = true)
  public void counterNoodling() throws Exception {
    IntraReq req = new IntraReq();
    req.add(Operations.assumeOp("myks", "mycountercf", "value", "LongType"))
            .add(Operations.assumeOp("myks", "mycountercf", "column", "UTF8Type"))
            .add(Operations.setKeyspaceOp("myks"))
            .add(Operations.setColumnFamilyOp("mycountercf"))
            .add(Operations.counter("counter_key", "counter_name_1", 1L).set("timeout", 30000))
            // 4
            .add(Operations.getOp("counter_key", "counter_name_1"))
            .add(Operations.counter("counter_key", "counter_name_1", new Long((long) Integer.MAX_VALUE+10L)).set("timeout", 30000))
            .add(Operations.getOp("counter_key", "counter_name_1"));

    IntraClient2 ic2 = new IntraClient2("localhost", 8080);
    IntraRes res = ic2.sendBlocking(req);
    List<Map> results = (List<Map>) res.getOpsRes().get("5");
    logger.info("has results {}", results);
    Assert.assertEquals(1, results.get(0).get("value"));
    results = (List<Map>) res.getOpsRes().get("7");   
    Assert.assertEquals(2147483658L, results.get(0).get("value"));
    Assert.assertTrue( (Long) results.get(0).get("value") > Integer.MAX_VALUE );
  }
}