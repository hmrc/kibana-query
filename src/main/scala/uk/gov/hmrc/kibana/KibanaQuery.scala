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
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.ws.ahc.AhcWSClient
import play.api.libs.ws.{WSAuthScheme, WSClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

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
        def f(name: String) = (source \ name).as[String]

        s"${f("@timestamp")}\t${f("request")}\t${f("http_referrer")}\t${f("sent_http_location")}"
      }

      println("$@timestamp\trequest\thttp_referrer\tsent_http_location")
      lines.foreach(println)
    }
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
