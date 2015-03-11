/*
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

package org.apache.hadoop.chukwa.datacollection.agent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.curator.test.TestingServer;
import org.apache.hadoop.chukwa.Chunk;
import org.apache.hadoop.chukwa.ChunkImpl;
import org.apache.hadoop.chukwa.conf.ChukwaConfiguration;
import org.apache.hadoop.chukwa.datacollection.ChunkQueue;
import org.apache.hadoop.chukwa.datacollection.DataFactory;
import org.apache.hadoop.chukwa.datacollection.adaptor.Adaptor;
import org.apache.hadoop.chukwa.datacollection.collector.CaptureWriter;
import org.apache.hadoop.chukwa.datacollection.connector.kafka.KafkaConnector;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;
import org.junit.Test;

import junit.framework.TestCase;
import kafka.server.KafkaConfig;
import kafka.server.KafkaServerStartable;

/**
 * This test case sets up a mini kafka and zookeeper cluster and
 * tests the kafka producer and consumer code
 */
public class TestKafka extends TestCase {
  private KafkaServerStartable server;
  //private ZooKeeperServerMain zkServer;
  private TestingServer zkServer;
  String KAFKA_PORT = "9092";
  int ZK_PORT = 21819;
  Configuration chukwaConfig = new Configuration();
  File dataDir;
  ChukwaAgent agent;
  Logger log = Logger.getLogger(TestKafka.class);
  int NUM_CHUNKS = 10;
  
  @Override
  protected void setUp() throws Exception{
    String tmpDir = System.getProperty("test.build.data", "/tmp");
    dataDir = new File(tmpDir+"/kafka");
    dataDir.mkdir();
    zkServer = new TestingServer(ZK_PORT, dataDir);
    zkServer.start();
    startLocalKafkaServer();    
    chukwaConfig = new ChukwaConfiguration();
    chukwaConfig.set("chukwa.pipeline", "org.apache.hadoop.chukwa.datacollection.collector.CaptureWriter"); //in memory writer
    chukwaConfig.set("chukwaAgent.chunk.queue", "org.apache.hadoop.chukwa.datacollection.agent.KafkaQueue"); //required
    chukwaConfig.set("chukwa.agent.connector", "org.apache.hadoop.chukwa.datacollection.connector.kafka.KafkaConnector"); //required
    chukwaConfig.set("kafkaQueue.reset", "true"); //make sure we read messages from the beginning (use only for testing)
    agent = new ChukwaAgent(chukwaConfig);
    //start collector to consume messages
    agent.connector = new KafkaConnector();
    agent.connector.start();
  }
  
  @Override
  protected void tearDown() throws IOException{
    agent.shutdown();
    server.shutdown();    
    zkServer.stop();
    deleteDir(dataDir);
  }

  @Test
  public void test() throws Exception{
    //add some data chunks to the queue    
    ChunkQueue queue = DataFactory.getInstance().getEventQueue();
    assertTrue(queue instanceof org.apache.hadoop.chukwa.datacollection.agent.KafkaQueue);
    String name = agent.processAddCommandE("add org.apache.hadoop.chukwa.datacollection.adaptor.ChukwaTestAdaptor TestData 0");
    Adaptor testAdaptor = agent.getAdaptor(name);
    for(int i = 1; i <= NUM_CHUNKS; i++){
      Chunk event = new ChunkImpl("TestData"+i, "TestStream", (long) i, ("chukwa-message"+i).getBytes(), testAdaptor);
      if(i == 5){
        event.setSource("someOtherHost");
      }
      queue.add(event);
    }
    
    //verify that the adaptor offset has increased
    long offset = agent.offset(testAdaptor).offset;
    System.out.println("Current offset = " + offset);
    assertTrue(offset == NUM_CHUNKS);    
    
    //wait for consumer to pick up the chunks
    Thread.sleep(5000);
    
    //verify that the chunk arrived at the writer
    ArrayList<Chunk> chunks = CaptureWriter.outputs;    
    System.out.println("Collected number of chunks:"+chunks.size());
    for(int j = 0; j < chunks.size(); j++){
      System.out.println(chunks.get(j).toString());
    }
    assertTrue(chunks.size() == NUM_CHUNKS);
  }
  
  private void startLocalKafkaServer(){
    //https://cwiki.apache.org/confluence/display/KAFKA/FAQ#FAQ-HowdoIwriteunittestsusingKafka?
    Properties kafkaServerProps = new Properties();
    kafkaServerProps.put("port", KAFKA_PORT);
    kafkaServerProps.put("broker.id", "1");
    kafkaServerProps.put("enable.zookeeper", "true");
    kafkaServerProps.put("zookeeper.connect", zkServer.getConnectString());
    kafkaServerProps.put("log.dirs", dataDir.getAbsolutePath()+"/kafka-logs");
    kafkaServerProps.put("auto.create.topics.enable", "true");
    KafkaConfig serverConfig = new KafkaConfig(kafkaServerProps);
    server = new KafkaServerStartable(serverConfig);
    server.startup();
    System.out.println("Kafka server started");
  }
  
  private void deleteDir(File dir) {
    if(dir.isDirectory()){
      File[] files = dir.listFiles();
      for(File file: files){
        deleteDir(file);
      }
    }
    dir.delete();    
  }
}
