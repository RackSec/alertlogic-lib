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
        customers-map {"123" "101"
                       "746228" "1111"}
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
        (check-output customer-data {"123" "101"})))
    (testing "handles many customers"
      (check-output customers customers-map))))

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
        (let [expected {"123" "101"
                        "746228" "1111"}
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
  (testing "handles some devices"
    (let [body (-> "test/hosts.edn" resource slurp read-string)
          fake-get (fake-get-success body)]
      (with-redefs [aleph.http/get fake-get]
        (let [expected (-> "test/processed-hosts.edn"
                           resource
                           slurp
                           read-string)
              output @(get-lm-devices-for-customer! "1111" "some-token")]
          (is (= expected output)))))))
