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

package org.apache.hadoop.chukwa.datacollection.connector.kafka;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kafka.consumer.ConsumerConfig;
import kafka.consumer.KafkaStream;
import kafka.consumer.Whitelist;
import kafka.javaapi.consumer.ConsumerConnector;

import org.apache.hadoop.chukwa.datacollection.agent.ChukwaAgent;
import org.apache.hadoop.chukwa.datacollection.connector.Connector;
import org.apache.hadoop.chukwa.util.ExceptionUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

/**
 * 
 * This Connector will setup multiple long running KafkaConsumer threads.
 * Each KafkaConsumer will setup its own pipeline of writers
 * 
 */
public class KafkaConnector implements Connector {
  Timer statTimer;
  volatile int chunkCount = 0;
  volatile boolean shutdown = false;
  { //instance initializer block
    statTimer = new Timer();
    statTimer.schedule(new TimerTask() {
      public void run() {
        int count = chunkCount;
        chunkCount = 0;
        log.info("# chunks ACK'ed from Kafka since last report: " + count);
      }
    }, 100, 60 * 1000);
  }
  ConsumerConfig consumerConfig;
  ConsumerConnector consumer;
  List<KafkaStream<byte[], byte[]>> streams;
  int NUM_THREADS = 5;
  Logger log = Logger.getLogger(KafkaConnector.class);
  ExecutorService executor;
  Configuration chukwaConf;
  ChukwaAgent agent;
  Set<String> topicSet = new HashSet<String>();
  
  //create a multi threaded kafka consumer which would write to the chunk queue 
  public KafkaConnector() throws Exception{
    this.agent = ChukwaAgent.getAgent();
    chukwaConf = agent.getConfiguration();
    configure(chukwaConf);
    executor = Executors.newFixedThreadPool(NUM_THREADS);
  }
  
  void configure(Configuration conf) throws Exception{
    String chukwaConfDir = System.getenv("CHUKWA_CONF_DIR");
    if(chukwaConfDir == null){
      throw new Exception("CHUKWA_CONF_DIR is not set. Shutting down KafkaQueue");
    }
    BufferedReader reader = new BufferedReader(new FileReader(chukwaConfDir+"/consumer.properties"));
    
    Properties props = new Properties();
    props.load(reader);
    
    if(conf.getBoolean("kafkaQueue.reset", false)){
      props.put("auto.offset.reset", "smallest");
      log.warn("Requested to reset offset of KafkaConsumer. Use this configuration parameter with caution");
    }    
    consumerConfig = new ConsumerConfig(props);
    consumer = kafka.consumer.Consumer.createJavaConsumerConnector(consumerConfig);   
  }

  @Override
  public void start() {
    try{
      String host = InetAddress.getLocalHost().getHostName();
      List<KafkaStream<byte[], byte[]>> streams = consumer.createMessageStreamsByFilter(new Whitelist("CHUKWA-(.*)"), NUM_THREADS);
      for(KafkaStream<byte[], byte[]> stream: streams){
        executor.submit(new KafkaConsumer(agent, stream));
      }
    } catch(Exception e){
      log.error(e);
      log.error(ExceptionUtil.getStackTrace(e));
    }    
  }

  @Override
  public void shutdown() {
    executor.shutdownNow();
  }

  @Override
  public void reloadConfiguration() {
  }
}
