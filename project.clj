(defproject alertlogic-lib "0.1.0"
  :description "A library for interacting with Alert Logic APIs."
  :lein-release {:deploy-via :clojars}
  :url "http://github.com/RackSec/alertlogic-lib"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [aleph "0.4.1"]
                 [base64-clj "0.1.1"]
                 [camel-snake-kebab "0.4.0"]
                 [cheshire "5.6.3"]
                 [com.taoensso/timbre "4.7.4"]
                 [manifold "0.1.5"]]
  :plugins [[lein-cljfmt "0.5.6" :exclusions [com.google.javascript/closure-compiler
                                              org.clojure/clojurescript]]
            [jonase/eastwood "0.2.3"]
            [lein-cloverage "1.0.9"]]
  :min-lein-version "2.7.0"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[pjstadig/humane-test-output "0.8.1"]]
                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]}})
