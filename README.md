# kibana-query

Quick demo showing how to querying Kibana from Scala.

Intended as a starting point rather than a production-ready utility, the fields to read and output file format are hard coded and you'll probably want to adjust them.

## Usage

    sbt 'runMain uk.gov.hmrc.kibana.KibanaQuery https://your-kibana.example.com your-kibana-username your-kibana-password your-kibana-query-file.json'

To obtain query JSON from the Kibana search UI, first press the up addow underneath the histogram and then choose 'Request' from the dropdown that appears. The 'Elasticsearch request body' JSON can then be copied and pasted into a file that can be used as the `your-kibana-query-file.json` argument.

## License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
