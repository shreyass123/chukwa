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

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.TimeZone;

import org.apache.hadoop.chukwa.Chunk;

import kafka.producer.KeyedMessage;

/**
 * This class dictates the topic name and key for the messages put in
 * kafka. It constructs KeyedMessage objects to be sent to Kafka broker. 
 * 
 * The current topic name has the format "CHUKWA-datatype-host"
 * The message key is the timestamp
 * 
 * In future we want to make this an interface and have the flexibility 
 * of having different chunks have different topics and kafka keys
 */

public class KafkaKeyFormatter {
  
  public static HashSet<String> topicSet = new HashSet<String>();
  private static final String CHUKWA = "CHUKWA-";

  List<KeyedMessage<byte[], byte[]>> buffers = new ArrayList<KeyedMessage<byte[], byte[]>>();

  public KeyedMessage<byte[], byte[]> format(Chunk event) throws IOException {
    long timeStamp = Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis();
    String timeTag = event.getTag("timeStamp");
    if(timeTag != null){
      timeStamp = Long.parseLong(timeTag);
    }
    String key = Long.toString(timeStamp);
    ByteArrayOutputStream buf = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(buf);
    event.write(dos);
    String topic = CHUKWA + event.getDataType();// + "-" + event.getSource();
    topicSet.add(topic);    
    KeyedMessage<byte[], byte[]> msg = new KeyedMessage<byte[], byte[]>(topic,
        key.getBytes(), buf.toByteArray());
    return msg;
  }
}
