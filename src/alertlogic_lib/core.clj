(ns alertlogic-lib.core
  (:require
   [clojure.string :as str]
   [aleph.http :as http]
   [base64-clj.core :as base64]
   [byte-streams :as bs]
   [camel-snake-kebab.core :refer [->kebab-case-keyword]]
   [cheshire.core :as json]
   [manifold.deferred :as md]
   [taoensso.timbre :as timbre :refer [info warn]]))

; NOTE(ehashman): depending on the API call, we need to use different base URLs
(def base-url-public "https://publicapi.alertlogic.net")
(def customer-api "/api/customer/v1/%s")

(def base-url "https://api.alertlogic.net")
(def lm-hosts-api "/api/lm/v1/%s/hosts")

(defn ^:private auth-header
  "The Alert Logic API handles authentication by accepting an
  API token as a username with an empty password."
  [api-token]
  (let [username-and-password (str api-token ":")
        encoded (base64/encode username-and-password)]
    {"Authorization" (str/join " " ["Basic" encoded])}))

(defn ^:private al-headers
  "The Alert Logic API exclusively serves JSON, so we must set
  the 'Accept' header."
  [api-token]
  (merge {"Accept" "application/json"}
         (auth-header api-token)))

(defn get-page!
  "Gets a page from the Alert Logic API.

  The url should be a full path. The api-token is provided
  for authentication by Alert Logic."
  [url api-token]
  (info "fetching" url)
  (let [headers (al-headers api-token)]
    (-> (md/chain
         (http/get url {:headers headers})
         :body
         bs/to-reader
         #(json/parse-stream % ->kebab-case-keyword))
        (md/catch
         Exception
         #(do (warn "problem fetching events page:" (.getMessage %))
              ::fetch-error)))))

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
    (:child-chain @(get-page! url api-token))))

(defn get-customers-map!
  "Given a root customer ID, returns a map of child customer
  IDs to Alert Logic IDs.

  Provided root-customer-id must correspond to an Alert Logic
  customer ID (integer string)."
  [root-customer-id api-token]
  (customer-json-to-id-map (get-customers! root-customer-id api-token)))

(defn get-lm-devices-for-customer!
  "Gets a list of devices active in the Alert Logic Log
  Manager for a given customer.

  Provided customer-id must be the Alert Logic customer ID
  (integer string)."
  [customer-id api-token]
  (let [url (str base-url-public (format lm-hosts-api customer-id))
        hosts (:hosts @(get-page! url api-token))
        cleanup-host
        (fn [host]
          (let [{{:keys [name status metadata]} :host} host]
            {:name name
             :status (:status status)
             :ips (:local-ipv-4 metadata)
             :type (:inst-type status)}))]
    (map cleanup-host hosts)))
