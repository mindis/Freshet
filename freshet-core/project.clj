(defproject org.pathirage.freshet/freshet-core "0.1.0-SNAPSHOT"
  :description "Freshet Core: CQL On Top Of Samza."
  :url "http://github.com/milinda/Freshet"
  :license {:name "Apache License, Version 2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0.html"}
  :repositories [["codehaus" "http://repository.codehaus.org/org/codehaus"]]
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.apache.samza/samza-api "0.7.0"]
                 [org.apache.samza/samza-serializers_2.10 "0.7.0"]
                 [org.apache.samza/samza-core_2.10 "0.7.0"]
                 [org.apache.samza/samza-yarn_2.10 "0.7.0"]
                 [org.apache.samza/samza-kv_2.10 "0.7.0"]
                 [org.apache.samza/samza-kafka_2.10 "0.7.0"]
                 [org.apache.kafka/kafka_2.10 "0.8.1"]
                 [org.slf4j/slf4j-api "1.6.2"]
                 [org.slf4j/slf4j-log4j12 "1.6.2"]
                 [com.google.guava/guava "18.0"]
                 [com.esotericsoftware/kryo "3.0.0"]
                 [org.codehaus.jackson/jackson-jaxrs "1.8.5"]
                 [org.apache.avro/avro "1.7.7"]
                 [org.schwering/irclib "1.10"]
                 [commons-codec/commons-codec "1.4"]]
  :source-paths      ["src/clojure"]
  :java-source-paths ["src/java"]
  :test-paths ["test/clojure" "test/java"]
  :javac-options ["-target" "1.6" "-source" "1.6" "-Xlint:-options"]
  :profiles {:test {:resource-paths ["test/resources"]}})
