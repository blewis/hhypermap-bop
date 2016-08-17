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

import org.junit.Assert.*
import org.junit.Test

class SentimentAnalyzerTest {

  @Test
  fun test() {
    val analyzer = SentimentAnalyzer("localhost:1234")
    try {
      assertEquals(SentimentAnalyzer.Sentiment.pos, analyzer.calcSentiment(":-)"))
      assertEquals(SentimentAnalyzer.Sentiment.neg, analyzer.calcSentiment(":-("))
    } finally {
      analyzer.close()
    }
  }
}