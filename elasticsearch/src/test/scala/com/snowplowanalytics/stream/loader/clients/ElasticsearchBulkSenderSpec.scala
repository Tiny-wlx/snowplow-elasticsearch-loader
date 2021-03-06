/**
 * Copyright (c) 2014-2017 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.stream.loader.clients

import com.snowplowanalytics.stream.loader.{CredentialsLookup, EmitterJsonInput, JsonRecord}
import org.json4s.jackson.JsonMethods._

// elastic4s
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.embedded.LocalNode

// scalaz
import scalaz._
import Scalaz._

import scala.concurrent.ExecutionContext.Implicits.global

// specs2
import org.specs2.mutable.Specification

class ElasticsearchBulkSenderSpec extends Specification {
  val node = LocalNode("es", System.getProperty("java.io.tmpdir"))
  node.start()
  val client       = node.elastic4sclient()
  val creds        = CredentialsLookup.getCredentialsProvider("a", "s")
  val documentType = "enriched"
  val sender = new ElasticsearchBulkSender(
    node.ip,
    node.port,
    false,
    "region",
    false,
    None,
    None,
    10000L,
    creds)

  val index = "idx"
  client.execute(createIndex(index)).await

  "send" should {
    "successfully send stuff" in {

      val validInput: EmitterJsonInput = "good" -> JsonRecord(
        parse("""{"s":"json"}"""),
        index,
        documentType).success
      val input = List(validInput)

      sender.send(input) must_== List.empty
      // eventual consistency
      Thread.sleep(1000)
      client.execute(search(index)).await.hits.head.sourceAsString must_== """{"s":"json"}"""
    }

    "report old failures" in {
      val data = List(("a", Failure(NonEmptyList("f"))))
      sender.send(data) must_== List(("a", Failure(NonEmptyList("f"))))
    }
  }
}
