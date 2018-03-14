(ns alertlogic-lib.core-test
  (:require [clojure.java.io :refer [resource]]
            [clojure.string :as s]
            [clojure.test :refer :all]
            [cheshire.core :as json]
            [manifold.deferred :as md]
            [taoensso.timbre :as timbre]
            [alertlogic-lib.core :as alc :refer :all]))

(defn fake-get-success
  [body]
  (fn [_addr _headers]
    (md/success-deferred {:body (json/generate-string body)})))

(deftest get-page!-tests
  (testing "blows up on exception"
    (let [fake-get
          (fn [_addr _headers]
            (md/error-deferred (Exception. "kaboom")))]
      (with-redefs [aleph.http/get fake-get]
        (is (thrown-with-msg? Exception #"kaboom" @(get-page! "" ""))))))
  (testing "returns deserialized json body"
    (let [fake-get (fake-get-success {:hosts []})]
      (with-redefs [aleph.http/get fake-get]
        (is (= {:hosts []}
               @(get-page! "" "")))))))

(def customers
  [{:customer-id 101 :customer-name "123-lol"}
   {:customer-id 1111 :customer-name "746228 Ltd."}])

(deftest customer-json-to-id-map-tests
  (let [cj->id #'alertlogic-lib.core/customer-json-to-id-map
        check-output (fn [input expected]
                       (is (= expected (cj->id input))))]
    (testing "handles empty case"
      (check-output [] {}))
    (testing "handles bad customer names"
      (let [customer-data [{:customer-id 101 :customer-name "lol"}]]
        (check-output customer-data {})))
    (testing "handles null customer names"
      (let [customer-data [{:customer-id 101 :customer-name nil}]]
        (check-output customer-data {})))
    (testing "handles good customer names"
      (let [customer-data [{:customer-id 101 :customer-name "123-lol"}]]
        (check-output customer-data {"123" ["101"]})))
    (testing "handles many customers"
      (let [customer-data {"123" ["101"]
                           "746228" ["1111"]}]
        (check-output customers customer-data)))
    (testing "handles repeat accounts"
      (let [customers (conj customers {:customer-id 111
                                       :customer-name "123-lol 2"})
            customer-data {"123" ["111" "101"]
                           "746228" ["1111"]}]
        (check-output customers customer-data)))))

(deftest get-customers!-tests
  (let [root-customer-data {:api-key "supar-sekret"
                            :customer-id 31337
                            :customer-name "SuperCustomer"
                            :child-chain customers}
        fake-get (fake-get-success root-customer-data)]
    (with-redefs [aleph.http/get fake-get]
      (testing "handles download"
        (let [expected customers
              output @(get-customers! "31337" "supar-sekret")]
          (is (= expected output))))
      (testing "handles download and formatting"
        (let [expected {"123" ["101"]
                        "746228" ["1111"]}
              output @(get-customers-map! "31337" "supar-sekret")]
          (is (= expected output)))))))

(defn use-atom-log-appender!
  "Adds a log observer that saves its log messages to an atom.

  Returns an atom that wraps a vector of possible log messages"
  []
  (let [log (atom [])
        log-appender-fn (fn [data]
                          (let [{:keys [output-fn]} data
                                formatted-output-str (output-fn data)]
                            (swap! log conj formatted-output-str)))]
    (timbre/merge-config!
     {:appenders
      {:atom-appender
       {:async false
        :enabled? true
        :min-level nil
        :output-fn :inherit
        :fn log-appender-fn}}})
    log))

(deftest get-prothosts!-tests
  (testing "taps out when id is null"
    (let [log (use-atom-log-appender!)]
      @(get-prothosts-for-customer! nil "some-token")
      (is (= 1 (count @log)))
      (is (s/includes? (first @log) "Customer ID cannot be nil. Aborting."))))
  (testing "handles an empty device list"
    (let [fake-get-page (fn [url token] {:hosts []})]
      (with-redefs [alc/get-page! fake-get-page]
        (is (empty? @(get-prothosts-for-customer! "1111" "some-token"))))))
  (testing "handles some devices"
    (let [prothost-body
          (-> "test/prothosts-faws.edn" resource slurp read-string)
          fake-get-page
          (fn [url token] prothost-body)]
    (with-redefs [alc/get-page! fake-get-page]
      (let [expected (-> "test/processed-prothosts-faws.edn"
                         resource
                         slurp
                         read-string)
            output @(get-prothosts-for-customer! "1111" "some token")]
        (is (= expected output)))))))

(deftest cleanup-host-tests
  (testing "Host data with relevant expected keys"
    (let [fake-host-data {:host {:status {:status "amazing"}
                                 :metadata {:local-ipv-4 ["10.15.0.1"
                                                          "192.168.0.13"]}
                                 :name "one okay host"}}
          expected {:metadata {:local-ipv-4 ["10.15.0.1" "192.168.0.13"]},
                    :name "one okay host",
                    :status-data {:status "amazing"},
                    :status "amazing",
                    :ips ["10.15.0.1" "192.168.0.13"]}
          output (cleanup-host fake-host-data)]
      (is (= expected output))))
  (testing "Input map lacks expected key"
    (let [bad-host-data {:host {:status {:status "Okay status in bad map"}
                                :name "bad-map-10000"}}
          expected {:status "Okay status in bad map"
                    :status-data {:status "Okay status in bad map"}
                    :ips nil
                    :name "bad-map-10000"}
          output (cleanup-host bad-host-data)]
      (is (= expected output))))
  (testing "nil input"
    (let [expected {:status nil
                    :ips nil}
          output (cleanup-host nil)]
      (is (= expected output)))))

(deftest cleanup-prothost-tests
  (testing "Protected host data with relevant expected keys, no error"
    (let [fake-prothost-data {:protectedhost {:status {:details []
                                                       :status "awesome"}
                                              :name "such a protected host"}}
          expected {:name "such a protected host"
                    :status-data {:details []
                                  :status "awesome"}
                    :tm-status "awesome"
                    :error nil}
          output (cleanup-prothost fake-prothost-data)]
      (is(= expected output))))
  (testing "Protected host data with relevant expected keys and error"
    (let [fake-prothost-data {:protectedhost {:status
                                              {:details [{:error "oh no"}]
                                               :status "not so awesome"}
                                              :name "not a protected host"}}
          expected {:name "not a protected host"
                    :status-data {:details [{:error "oh no"}]
                                  :status "not so awesome"}
                    :tm-status "not so awesome"
                    :error "oh no"}
          output (cleanup-prothost fake-prothost-data)]
      (is (= expected output))))
  (testing "Protected host map lacks expected key"
    (let [fake-prothost-data {:protectedhost {:name "malformed data"}}
          expected {:name "malformed data"
                    :tm-status nil
                    :error nil}
          output (cleanup-prothost fake-prothost-data)]
      (is (= expected output))))
  (testing "nil input"
    (let [expected {:tm-status nil
                    :error nil}
          output (cleanup-prothost nil)]
      (is (= expected output)))))

(deftest get-lm-devices!-tests
  (testing "taps out when id is null"
    (let [log (use-atom-log-appender!)]
      @(get-lm-devices-for-customer! nil "some-token")
      (is (= 1 (count @log)))
      (is (s/includes? (first @log) "Customer ID cannot be nil. Aborting."))))
  (testing "handles an empty device list"
    (let [fake-get (fake-get-success {:hosts []})]
      (with-redefs [aleph.http/get fake-get]
        (is (empty? @(get-lm-devices-for-customer! "1111" "some-token"))))))
  (testing "handles some devices with no protected host reply"
    (let [body (-> "test/hosts.edn" resource slurp read-string)
          fake-get (fake-get-success body)]
      (with-redefs [aleph.http/get fake-get]
        (let [expected (-> "test/processed-hosts.edn"
                           resource
                           slurp
                           read-string)
              output @(get-lm-devices-for-customer! "1111" "some-token")]
          (is (= expected output))))))
  (testing "handles some devices with protected hosts"
    ;; mock both host and protectedhost endpoints
    (let [hosts-url
          "https://publicapi.alertlogic.net/api/lm/v1/1111/hosts"
          prothosts-url
          "https://publicapi.alertlogic.net/api/tm/v1/1111/protectedhosts"
          hosts-body
          (-> "test/hosts-faws.edn" resource slurp read-string)
          prothosts-body
          (-> "test/prothosts-faws.edn" resource slurp read-string)
          fake-get-page (fn [url token]
                          (cond
                            (= url hosts-url) hosts-body
                            (= url prothosts-url) prothosts-body
                            :else "oh no, unexpected url"))]
      (with-redefs [alc/get-page! fake-get-page]
        (let [expected (-> "test/processed-hosts-faws.edn"
                           resource
                           slurp
                           read-string)
              output @(get-lm-devices-for-customer! "1111" "some-token")]
          (is (= expected output)))))))

(deftest get-sources!-tests
  (testing "taps out when id is null"
    (let [log (use-atom-log-appender!)]
      @(get-sources-for-customer! nil "some-token")
      (is (= 1 (count @log)))
      (is (s/includes? (first @log) "Customer ID cannot be nil. Aborting."))))
  (testing "handles an empty device list"
    (let [fake-get (fake-get-success {:hosts []})]
      (with-redefs [aleph.http/get fake-get]
        (is (empty? @(get-sources-for-customer! "1111" "some-token"))))))
  (testing "handles some devices (DED)"
    (let [body (-> "test/sources.edn" resource slurp read-string)
          fake-get (fake-get-success body)]
      (with-redefs [aleph.http/get fake-get]
        (let [expected (-> "test/processed-sources.edn"
                           resource
                           slurp
                           read-string)
              output @(get-sources-for-customer! "1111" "some-token")]
          (is (= expected output))))))
  (testing "handles some devices (FAWS)"
    (let [body (-> "test/sources-faws.edn" resource slurp read-string)
          fake-get (fake-get-success body)]
      (with-redefs [aleph.http/get fake-get]
        (let [expected (-> "test/processed-sources-faws.edn"
                           resource
                           slurp
                           read-string)
              output @(get-sources-for-customer! "1111" "some-token")]
          (is (= expected output)))))))
