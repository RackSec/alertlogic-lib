(ns alertlogic-lib.core
  (:require
   [clojure.set :refer [rename-keys]]
   [clojure.string :as str]
   [aleph.http :as http]
   [byte-streams :as bs]
   [camel-snake-kebab.core :refer [->kebab-case-keyword]]
   [cheshire.core :as json]
   [manifold.deferred :as md]
   [taoensso.timbre :as timbre :refer [info error warn]]))

; NOTE(ehashman): depending on the API call, we need to use different base URLs
(def base-url-public "https://publicapi.alertlogic.net")
(def customer-api "/api/customer/v1/%s")

(def base-url "https://api.alertlogic.net")
(def lm-hosts-api "/api/lm/v1/%s/hosts")

(defn get-page!
  "Gets a page from the Alert Logic API.

  The url should be a full path. The api-token is provided
  for authentication by Alert Logic.

  The Alert Logic API exclusively serves JSON, so we must set
  the 'Accept' header."
  [url api-token]
  (info "Fetching" url)
  (md/chain
   (http/get url {:headers {"Accept" "application/json"}
                  :basic-auth [api-token]})
   :body
   bs/to-reader
   #(json/parse-stream % ->kebab-case-keyword)))

(defn ^:private customer-json-to-id-map
  "Immutable logic for processing customer JSON, returning a
  map of customer IDs to AlertLogic IDs.

  We rely on the convention that a 'customer ID' is a string
  of digits at the beginning of each child account's name."
  [customer-json]
  (let [cust-id-pattern #"^\d+"
        cleanup-customer
        (fn [{customer-name :customer-name, al-id :customer-id}]
          (let [customer-id (and customer-name  ;; don't match null name
                                 (re-find cust-id-pattern customer-name))]
            {:id customer-id
             :al-id al-id}))
        id-list (->> (map cleanup-customer customer-json)
                     (remove #(nil? (:id %))))  ;; might be some non-matches
        add-customer-to-map
        (fn [customer-map customer]
          (assoc customer-map (:id customer) (str (:al-id customer))))]
    (reduce add-customer-to-map {} id-list)))

(defn get-customers!
  "Fetches customer info from the Alert Logic API."
  [root-customer-id api-token]
  (let [url (str base-url (format customer-api root-customer-id))]
    (md/chain (get-page! url api-token)
              :child-chain)))

(defn get-customers-map!
  "Given a root customer ID, returns a map of child customer
  IDs to Alert Logic IDs.

  Provided root-customer-id must correspond to an Alert Logic
  customer ID (integer string)."
  [root-customer-id api-token]
  (customer-json-to-id-map @(get-customers! root-customer-id api-token)))

(defn get-lm-devices-for-customer!
  "Gets a list of devices active in the Alert Logic Log
  Manager for a given customer.

  Provided customer-id must be the Alert Logic customer ID
  (integer string)."
  [customer-id api-token]
  (if (nil? customer-id)
    (do
      (error "Customer ID cannot be nil. Aborting.")
      (md/success-deferred []))
    (let [url (str base-url-public (format lm-hosts-api customer-id))
          cleanup-host
          (fn [{:keys [host]}]
            (let [{:keys [status metadata]} host]
              (merge
               (rename-keys host {:status :status-data})
               {:status (:status status)
                :ips (:local-ipv-4 metadata)})))]
      (md/chain
       (get-page! url api-token)
       :hosts
       #(map cleanup-host %)))))  ;; transducer version of map doesn't work here
