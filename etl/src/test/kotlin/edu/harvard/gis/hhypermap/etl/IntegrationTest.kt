/*
 * Copyright 2016 President and Fellows of Harvard College
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.harvard.gis.hhypermap.etl

import io.confluent.examples.streams.IntegrationTestUtils
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.DecoderFactory
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.LongDeserializer
import org.apache.kafka.common.serialization.LongSerializer
import org.apache.kafka.streams.KafkaStreams
import org.apache.kafka.streams.KeyValue
import org.apache.kafka.streams.StreamsConfig
import org.junit.After
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.test.assertEquals


class IntegrationTest {
  companion object {
    val avroDecoderFactory = DecoderFactory.get()!!
    val avroSchema = Schema.Parser().parse(File("../avro/tweet.avsc"))!!
  }

  var kafkaStreams: KafkaStreams? = null

  @After
  fun after() {
    println("Closing KafkaStreams")
    kafkaStreams?.close()
  }

  @Test
  fun test() {
    // use the current time in a readable way just to troubleshoot which record/topic this is
    val createdAt = DateTimeFormatter.ofPattern("ddHHmmss").format(LocalDateTime.now())//day hour minute second

    // note: if this test is executed on a pre-existing cluster that sticks around between runs,
    //  then set the suffix to something unique per run.
    val kafkaSuffix = "-$createdAt"

    val etlConfig = buildConfig(File("etl.yml"))
    etlConfig.kafkaSourceTopic = "etl-integrationTest-in$kafkaSuffix"
    etlConfig.kafkaDestTopic = "etl-integrationTest-out$kafkaSuffix"
    etlConfig.kafkaStreamsConfig[StreamsConfig.APPLICATION_ID_CONFIG] = "etl-integrationTest-streams$kafkaSuffix"
    etlConfig.kafkaStreamsConfig[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest" // important

    val defProperties = Properties() // for write & read to Kafka in this test
    for (prop in listOf(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "schema.registry.url")) {
      defProperties[prop] = etlConfig.kafkaStreamsConfig[prop]
    }

    println("--")
    println("-- Write to Kafka")
    println("--")

    // Create test data

    val inputRecord = jsonToAvro(
            """{"id":$createdAt, "created_at":$createdAt, "user_screen_name":"DavidWSmiley",
"coord_lat":42.3, "coord_lon":-70.0, "text":"I feel happy", "lang":"und",
"sentiment":"und"}""")
    val inputRecords = listOf(KeyValue(inputRecord.get("id"), inputRecord))

    // Write to Kafka
    val producerConfig = defProperties.clone() as Properties
    producerConfig.put(ProducerConfig.ACKS_CONFIG, "all")
    producerConfig.put(ProducerConfig.RETRIES_CONFIG, 0)
    producerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, LongSerializer::class.java)
    producerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer::class.java)

    IntegrationTestUtils.produceKeyValuesSynchronously(etlConfig.kafkaSourceTopic!!,
            inputRecords, producerConfig)

    println("--")
    println("-- Streams Start, ETL/Enrich")
    println("--")

    kafkaStreams = buildStreams(etlConfig)
    // note: Kafka Streams requires the source topic(s) to exist already, otherwise you get an
    //  exception.
    kafkaStreams!!.start() // note:we close in @After
//    println("Sleeping")
//    Thread.sleep(2000) // enough time to start consuming

    println("--")
    println("-- Read/verify output")
    println("--")

    val consumerConfig = defProperties.clone() as Properties
    consumerConfig.put(ConsumerConfig.GROUP_ID_CONFIG, "etl-integrationTest-consumer$kafkaSuffix")
    consumerConfig.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest") // important
    consumerConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, LongDeserializer::class.java)
    consumerConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer::class.java)

    val actualValues: List<GenericRecord> =
            IntegrationTestUtils.waitUntilMinValuesRecordsReceived(consumerConfig,
              etlConfig.kafkaDestTopic!!, inputRecords.size)

    kafkaStreams!!.close()

    val resultRecord = actualValues[0]
    println(resultRecord)
    assertEquals("pos", resultRecord.get("sentiment").toString())
  }



  private fun jsonToAvro(jsonString: String): GenericRecord {
    val reader = GenericDatumReader<Any>(avroSchema)
    return reader.read(null, avroDecoderFactory.jsonDecoder(avroSchema, jsonString)) as GenericRecord
  }
}