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
package org.apache.hadoop.chukwa.datacollection.adaptor;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Arrays;
import java.util.Properties;

import org.apache.hadoop.chukwa.ChunkImpl;
import org.apache.hadoop.chukwa.util.ExceptionUtil;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.log4j.Logger;

/**
 * Kafka consumer adaptor to consume from the kafka queue and write to the destination
 *
 */
public class KafkaAdaptor extends AbstractAdaptor {
	private Logger log = Logger.getLogger(KafkaAdaptor.class);
	private KafkaConsumer<String, byte[]> consumer;
	private String streamName = "kafkaDefaultStream";
	private volatile boolean shutdown = false;
	private ConsumerThread thread;
	private String consumerPropsFile;
	private int interval;
	
	private class ConsumerThread extends Thread {
		KafkaAdaptor adaptor;
		long offset = 0;
		
		ConsumerThread(KafkaAdaptor adaptor){
			this.adaptor = adaptor;
		}
		
		long getOffset() {
			//TODO:maybe wait until the thread dies? Note that getCurrentStatus also calls this
			return offset;
		}
		@Override
		public void run() {
			//TODO: use manual offset control 
			consumer.subscribe(Arrays.asList("metrics", "logs"));
			while (!shutdown) {
				ConsumerRecords<String, byte[]> records = consumer.poll(interval);
				for (ConsumerRecord<String, byte[]> record : records) {
					log.info(String.format("offset = %d, key = %s, value = %s", record.offset(), record.key(),
							record.value()));
					// TODO: use avro
					ChunkImpl chunk = new ChunkImpl(type, streamName, record.offset(), record.value(), adaptor);
					offset = record.offset();
					try {
						dest.add(chunk);
					} catch (InterruptedException e) {
						log.info("Adaptor interrupted:" + ExceptionUtil.getStackTrace(e));
						break;
					}
				}
			}
		}
	}
	
	@Override
	public String getCurrentStatus() {
		return type.trim() + " " + thread.getOffset() + " " + streamName;
	}

	@Override
	public long shutdown(AdaptorShutdownPolicy shutdownPolicy) throws AdaptorException {
		shutdown = true;
		return thread.getOffset();
	}

	@Override
	public void start(long offset) throws AdaptorException {
		Properties props = new Properties();
		BufferedReader reader;
		try {
			reader = new BufferedReader(new FileReader(consumerPropsFile));
			props.load(reader);
		} catch (Exception e) {
			throw new AdaptorException(e);
		}
		consumer = new KafkaConsumer<String, byte[]>(props);
		thread = new ConsumerThread(this);
		thread.start();
	}

	@Override
	public String parseArgs(String s) {
		// KafkaAdaptor consumer.propertiesFilePath streamName [interval]
		String[] tokens = s.split(" ");
		if (tokens.length == 3) {
			consumerPropsFile = tokens[0];
			streamName = tokens[1];
			interval = Integer.parseInt(tokens[2]);
		} else if(tokens.length == 2) {
			consumerPropsFile = tokens[0];
			streamName = tokens[1];
			interval = 100;
		} else{
			log.error("bad args for KafkaAdaptor, should pass consumer.propertiesFilePath streamName [interval]");
			return null;
		}
		return s;
	}

}
