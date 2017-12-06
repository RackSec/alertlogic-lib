(defproject alertlogic-lib "0.3.0"
  :description "A library for interacting with Alert Logic APIs."
  :lein-release {:deploy-via :clojars}
  :url "http://github.com/RackSec/alertlogic-lib"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [aleph "0.4.3"]
                 [camel-snake-kebab "0.4.0"]
                 [cheshire "5.8.0"]
                 [com.taoensso/timbre "4.10.0"]
                 [manifold "0.1.6"]]
  :plugins [[lein-cljfmt "0.5.6" :exclusions [com.google.javascript/closure-compiler
                                              org.clojure/clojurescript]]
            [jonase/eastwood "0.2.3"]
            [lein-cloverage "1.0.9"]]
  :min-lein-version "2.7.0"
  :profiles {:uberjar {:aot :all}
             :repl {:main alertlogic-lib.core}
             :dev {:dependencies [[pjstadig/humane-test-output "0.8.3"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}})
