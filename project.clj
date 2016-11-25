(defproject alertlogic-lib "0.1.0-SNAPSHOT"
  :description "A library for interacting with Alert Logic APIs."
  :url "http://github.com/RackSec/alertlogic-lib"
  :license {:name "proprietary"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [aleph "0.4.1-beta2"]
                 [base64-clj "0.1.1"]
                 [camel-snake-kebab "0.4.0"]
                 [cheshire "5.5.0"]
                 [com.taoensso/timbre "4.2.0"]
                 [manifold "0.1.4"]]
  :plugins [[lein-cljfmt "0.3.0"]
            [jonase/eastwood "0.2.3"]
            [lein-cloverage "1.0.7-SNAPSHOT"]]
  :min-lein-version "2.0.0"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[pjstadig/humane-test-output "0.8.1"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}})
