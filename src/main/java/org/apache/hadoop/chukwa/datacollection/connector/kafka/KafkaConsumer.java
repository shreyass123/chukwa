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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kafka.consumer.ConsumerIterator;
import kafka.consumer.KafkaStream;

import org.apache.hadoop.chukwa.Chunk;
import org.apache.hadoop.chukwa.ChunkImpl;
import org.apache.hadoop.chukwa.datacollection.agent.ChukwaAgent;
import org.apache.hadoop.chukwa.datacollection.writer.ChukwaWriter;
import org.apache.hadoop.chukwa.datacollection.writer.PipelineStageWriter;
import org.apache.hadoop.chukwa.datacollection.writer.WriterException;
import org.apache.hadoop.chukwa.util.ExceptionUtil;
import org.apache.hadoop.conf.Configuration;
import org.apache.log4j.Logger;

/**
 * Multi-threaded kafka consumer which reads from kafka brokers and 
 * each writes to the configured pipeline
 *
 */
public class KafkaConsumer implements Runnable {
  Configuration chukwaConf;
  ChukwaAgent agent;
  KafkaStream<byte[], byte[]> stream;
  protected ChukwaWriter writers = null;
  Logger log = Logger.getLogger(KafkaConsumer.class);
  KafkaConsumer(ChukwaAgent agent, KafkaStream<byte[], byte[]> stream) throws WriterException {
    this.stream = stream;
    this.agent = agent;
    this.chukwaConf = agent.getConfiguration();
    writers = new PipelineStageWriter(chukwaConf);
  }
  
  @Override
  public void run() {
    log.info("Running KafkaConsumer for stream:"+stream.clientId());
    ConsumerIterator<byte[], byte[]> iter = stream.iterator();
    List<Chunk> chunks = new ArrayList<Chunk>();
    //int chunkBufferCount = chukwaConf.get("chukwaAgent.kafka.consumer.buffercount");
    while(iter.hasNext()){
      byte[] bytes = iter.next().message();
      //byte[] meta = iter.next().key();
      try {
        Chunk chunk = ChunkImpl.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        chunks.add(chunk);
        writers.add(chunks);
      } catch (IOException e) {
        log.error("IOException reading chunk. " + e.getMessage());
        log.error(ExceptionUtil.getStackTrace(e));
      } catch (WriterException e) {
        log.error("Writer exception in pipeline writer. " + e.getMessage());
        log.error(ExceptionUtil.getStackTrace(e));
      }
      chunks.clear();
    }
    log.info("KafkaConsumer shutdown");
  }
}
