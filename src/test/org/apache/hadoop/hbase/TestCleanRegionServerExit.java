/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase;

import java.io.IOException;
import java.util.TreeMap;

import org.apache.hadoop.io.Text;

/**
 * Tests region server failover when a region server exits.
 */
public class TestCleanRegionServerExit extends HBaseClusterTestCase {
  private HClient client;

  /** constructor */
  public TestCleanRegionServerExit() {
    super();
    conf.setInt("ipc.client.timeout", 5000);            // reduce ipc client timeout
    conf.setInt("ipc.client.connect.max.retries", 5);   // and number of retries
    conf.setInt("hbase.client.retries.number", 2);      // reduce HBase retries
  }
  
  /**
   * {@inheritDoc}
   */
  @Override
  public void setUp() throws Exception {
    super.setUp();
    this.client = new HClient(conf);
  }
  
  /**
   * The test
   * @throws IOException
   */
  public void testCleanRegionServerExit() throws IOException {
    // When the META table can be opened, the region servers are running
    this.client.openTable(HConstants.META_TABLE_NAME);
    // Put something into the meta table.
    String tableName = getName();
    HTableDescriptor desc = new HTableDescriptor(tableName);
    desc.addFamily(new HColumnDescriptor(HConstants.COLUMN_FAMILY.toString()));
    this.client.createTable(desc);
    // put some values in the table
    this.client.openTable(new Text(tableName));
    Text row = new Text("row1");
    long lockid = client.startUpdate(row);
    client.put(lockid, HConstants.COLUMN_FAMILY,
        tableName.getBytes(HConstants.UTF8_ENCODING));
    client.commit(lockid);
    // Start up a new region server to take over serving of root and meta
    // after we shut down the current meta/root host.
    this.cluster.startRegionServer();
    // Now shutdown the region server and wait for it to go down.
    this.cluster.stopRegionServer(0);
    this.cluster.waitOnRegionServer(0);
    
    // Verify that the client can find the data after the region has been moved
    // to a different server

    HScannerInterface scanner =
      client.obtainScanner(HConstants.COLUMN_FAMILY_ARRAY, new Text());

    try {
      HStoreKey key = new HStoreKey();
      TreeMap<Text, byte[]> results = new TreeMap<Text, byte[]>();
      while (scanner.next(key, results)) {
        assertTrue(key.getRow().equals(row));
        assertEquals(1, results.size());
        byte[] bytes = results.get(HConstants.COLUMN_FAMILY);
        assertNotNull(bytes);
        assertTrue(tableName.equals(new String(bytes, HConstants.UTF8_ENCODING)));
      }
      System.out.println("Success!");
    } finally {
      scanner.close();
    }
  }
}
