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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import kafka.javaapi.producer.Producer;
import kafka.producer.ProducerConfig;

import org.apache.hadoop.chukwa.Chunk;
import org.apache.hadoop.chukwa.datacollection.ChunkQueue;
import org.apache.hadoop.chukwa.datacollection.writer.hbase.Reporter;
import org.apache.hadoop.chukwa.util.ExceptionUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

public class KafkaQueue implements ChunkQueue{
  ChukwaAgent agent;
  ProducerConfig producerConfig;
  Configuration chukwaConf;
  String defaultProcessor;
  KafkaKeyFormatter formatter;
  Reporter reporter;
  Logger log = Logger.getLogger(KafkaQueue.class);
  Producer<byte[], byte[]> producer;
  
  private void configure(Configuration conf) throws Exception{
    agent = ChukwaAgent.getAgent();
    String chukwaConfDir = System.getenv("CHUKWA_CONF_DIR");
    if(chukwaConfDir == null){
      throw new Exception("CHUKWA_CONF_DIR is not set. Shutting down KafkaQueue");
    }
    BufferedReader reader = new BufferedReader(new FileReader(chukwaConfDir+"/producer.properties"));
    Properties props = new Properties();
    props.load(reader);
    producerConfig = new ProducerConfig(props);
  }
  
  public KafkaQueue(Configuration conf) throws Exception{
    formatter = new KafkaKeyFormatter();
    this.chukwaConf = conf;
    configure(conf);
    producer = new Producer<byte[], byte[]>(producerConfig);
  }

  @Override
  public synchronized void add(Chunk event) throws InterruptedException {
    try {
      producer.send(formatter.format(event));      
      //increase the adaptor offset
      agent.reportCommit(event.getInitiator(), event.getSeqID());
    } catch (IOException e) {
      log.error(e);
      log.debug(ExceptionUtil.getStackTrace(e));
    }
  }

  @Override
  public void collect(List<Chunk> chunks, int count)
      throws InterruptedException {
    log.error("Unsupported operation KakfaQueue.collect. Please configure kafka connector when using this queue");
  }

  @Override
  public int size() {
    return 0;
  }

}
