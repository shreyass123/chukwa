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
package org.apache.hadoop.chukwa.datacollection.writer.kafka;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.hadoop.chukwa.Chunk;
import org.apache.hadoop.chukwa.datacollection.writer.ChukwaWriter;
import org.apache.hadoop.chukwa.datacollection.writer.PipelineableWriter;
import org.apache.hadoop.chukwa.datacollection.writer.WriterException;
import org.apache.hadoop.conf.Configuration;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Logger;

/**
 * A PipelineableWriter implementing the kafka producer
 */
public class KafkaWriter extends PipelineableWriter{
	private Logger log = Logger.getLogger(KafkaWriter.class);
	private Producer<String, byte[]> producer;
	@Override
	public void init(Configuration c) throws WriterException {
		String producerPropsFile = c.get("kafka.writer.producer.properties");
		Properties props = new Properties();
		try {
			BufferedReader reader = new BufferedReader(new FileReader(producerPropsFile));
			props.load(reader);
		} catch (Exception e) {
			log.error("Could not load the producer properties file. Trying defaults.");
			props.put("bootstrap.servers", "localhost:4242");
			props.put("acks", "all");
			props.put("retries", 0);
			props.put("batch.size", 16384);
			props.put("linger.ms", 1);
			props.put("buffer.memory", 33554432);
			props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
			props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
			// throw new WriterException(e);
		}
		producer = new KafkaProducer<String, byte[]>(props);
	}

	@Override
	public void close() throws WriterException {
		producer.close();		
	}
	
	@Override
	public CommitStatus add(List<Chunk> chunks){
		for(Chunk chunk: chunks){			
			String timestamp = chunk.getTag("timeStamp");
		    String key = timestamp;
			if(timestamp == null || timestamp.equals("")){
				key = Long.toString(Calendar.getInstance(TimeZone.getTimeZone("UTC")).getTimeInMillis());
			}
			producer.send(new ProducerRecord<String, byte[]>(key, chunk.getData()));
		}
		return ChukwaWriter.COMMIT_OK;
	}
}
