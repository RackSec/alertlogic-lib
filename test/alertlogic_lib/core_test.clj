(ns alertlogic-lib.core-test
  (:require [clojure.java.io :refer [resource]]
            [clojure.test :refer :all]
            [cheshire.core :as json]
            [manifold.deferred :as md]
            [alertlogic-lib.core :as alc :refer :all]))

(deftest test-al-headers
  (let [token "bf03a8fa9d0c6a36dec683675e88489b"
        base64-token "YmYwM2E4ZmE5ZDBjNmEzNmRlYzY4MzY3NWU4ODQ4OWI6"]
    (testing "ensure auth header is correctly formatted"
      (let [expected {"Authorization" (str "Basic " base64-token)}
            output (#'alc/auth-header token)]
        (is (= expected output))))
    (testing "ensure we get the correct headers"
      (let [expected {"Accept" "application/json"
                      "Authorization" (str "Basic " base64-token)}
            output (#'alc/al-headers token)]
        (is (= expected output))))))

(defn fake-get-success
  [body]
  (fn [_addr _headers]
    (md/success-deferred {:body (json/generate-string body)})))

(deftest get-page!-tests
  (testing "returns ::fetch-error on exception"
    (let [fake-get
          (fn [_addr _headers]
            (md/error-deferred (Exception. "kaboom")))]
      (with-redefs [aleph.http/get fake-get]
        (is (= :alertlogic-lib.core/fetch-error
               @(get-page! "" ""))))))
  (testing "returns deserialized json body"
    (let [fake-get (fake-get-success {:hosts []})]
      (with-redefs [aleph.http/get fake-get]
        (is (= {:hosts []}
               @(get-page! "" "")))))))

(deftest get-customers!-tests
  (let [cj->id #'alertlogic-lib.core/customer-json-to-id-map
        customers [{:customer-id 101 :customer-name "123-lol"}
                   {:customer-id 1111 :customer-name "746228 Ltd."}]
        customers-map {"123" "101"
                       "746228" "1111"}
        check-output (fn [input expected]
                       (is (= expected (cj->id input))))]
    (testing "handles empty case"
      (check-output [] {}))
    (testing "handles bad customer names"
      (let [customer-data [{:customer-id 101 :customer-name "lol"}]]
        (check-output customer-data {})))
    (testing "handles good customer names"
      (let [customer-data [{:customer-id 101 :customer-name "123-lol"}]]
        (check-output customer-data {"123" "101"})))
    (testing "handles many customers"
      (check-output customers customers-map))
    (testing "handles download and handover"
      (let [root-customer-data {:api-key "supar-sekret"
                                :customer-id 31337
                                :customer-name "SuperCustomer"
                                :child-chain customers}
            fake-get (fake-get-success root-customer-data)]
        (with-redefs [aleph.http/get fake-get]
          (let [expected {"123" "101"
                          "746228" "1111"}
                output (get-customers! "31337" "supar-sekret")]
            (is (= expected output))))))))

(deftest get-lm-devices!-tests
  (testing "handles an empty device list"
    (let [fake-get (fake-get-success {:hosts []})]
      (with-redefs [aleph.http/get fake-get]
        (is (empty? (get-lm-devices-for-customer! "1111" "some-token"))))))
  (testing "handles some devices"
    (let [body (read-string (slurp (resource "test/hosts.edn")))
          fake-get (fake-get-success body)]
      (with-redefs [aleph.http/get fake-get]
        (let [expected '({:name "123455"
                          :type "appliance"
                          :status "ok"
                          :ips ["172.16.10.5"]}
                         {:name "987654"
                          :type "host"
                          :status "offline"
                          :ips ["10.15.0.1" "192.168.0.13"]})
              output (get-lm-devices-for-customer! "1111" "some-token")]
          (is (= expected output)))))))
