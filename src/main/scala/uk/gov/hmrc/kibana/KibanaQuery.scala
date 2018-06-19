/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.kibana

import java.io.FileInputStream

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}
import play.api.libs.json.{JsObject, JsString, JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.util.matching.Regex

object KibanaQuery {

  def main(args: Array[String]): Unit = {
    args match {
      case Array(kibanaUrl, username, password, queryJsonFilename) =>
        val future = withWSClient(ws => runQuery(ws, kibanaUrl, username, password, queryJsonFilename))
        Await.result(future, 60 seconds)
      case _ =>
        System.err.println("Usage: KibanaQuery <kibana URL> <username> <password> <query JSON filename>")
        sys.exit(1)
    }
  }

  private def withWSClient[T](body: WSClient => Future[T]): Future[T] = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: Materializer = ActorMaterializer()
    val ws = AhcWSClient()

    body(ws)
      .andThen { case _ => ws.close() }
      .andThen { case _ => system.terminate() }
  }

//  private final val Fields = Seq("@timestamp", "request", "http_referrer", "sent_http_location")
  private final val Fields = Seq("@timestamp", "request", "status", "upstream_status", "request_time", "upstream_http_server", "upstream_response_length")

  private def runQuery(ws: WSClient, kibanaUrl: String, username: String, password: String, queryJsonFilename: String): Future[Unit] = {
    val query = parseJsonFile(queryJsonFilename)
    ws.url(searchUrl(kibanaUrl))
      .withAuth(username, password, WSAuthScheme.BASIC)
      .withHttpHeaders(
        "kbn-xsrf-token" -> "kibana",
        "kbn-version" -> "5.5.3"
      )
      .post(query).map { response =>

      if (response.status != 200) sys.error(s"Got response status ${response.status}: ${response.statusText}")

      val hitSources = (response.json \ "hits" \ "hits").as[Seq[JsObject]].map(hit => (hit \ "_source").as[JsObject])

      val lines = hitSources.map { source =>
        def f(name: String): String = {
          val jsValue = (source \ name).get
          jsValue match {
            // don't surround strings with ""
            case s: JsString => s.value
            case v => v.toString
          }
        }

        val values = Fields.map(f _ andThen maskNino andThen maskRenewalBarcode).mkString("\t")
        values
      }

      val headings = Fields.mkString("\t")
      println(headings)
      lines.foreach(println)
    }
  }

  private val ninoRegex: Regex = "((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?".r.unanchored
  private def maskNino(s: String): String = {
    ninoRegex.replaceAllIn(s, "$1???????")
  }

  private val renewalBarcodeRegex = "/renewals/[0-9]{15}".r.unanchored
  private def maskRenewalBarcode(s: String): String = {
    renewalBarcodeRegex.replaceAllIn(s, "/renewals/???????????????")
  }

  private def searchUrl(kibanaUrl: String) = s"$kibanaUrl/elasticsearch/*/_search"

  private def parseJsonFile(queryJsonFilename: String): JsValue = {
    val stream = new FileInputStream(queryJsonFilename)
    try {
      Json.parse(stream)
    }
    finally {
      stream.close()
    }
  }
}
