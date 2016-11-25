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

(def base-url "https://publicapi.alertlogic.net")

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

  The uri should begin with a '/'. The api-token is provided
  for authentication by Alert Logic."
  [uri api-token]
  (info "fetching" uri)
  (let [headers (al-headers api-token)
        url (str/join "" [base-url uri])]
    (-> (md/chain
         (http/get url {:headers headers})
         :body
         bs/to-reader
         #(json/parse-stream % ->kebab-case-keyword))
        (md/catch
         Exception
         #(do (warn "problem fetching events page:" (.getMessage %))
              ::fetch-error)))))

(defn get-lm-devices-for-customer!
  "Gets a list of devices active in the Alert Logic Log
  Manager for a given customer.

  Provided customer-id must be the Alert Logic customer ID
  (an integer)."
  [customer-id api-token]
  (let [uri (str/join "" ["/api/lm/v1/" customer-id "/hosts"])
        hosts (:hosts @(get-page! uri api-token))]
    (for [host hosts
          :let [{{:keys [name status metadata]} :host} host
                device-status (:status status)
                device-ips (:local-ipv-4 metadata)
                device-type (:inst-type status)]]
      {:name name
       :status device-status
       :ips device-ips
       :type device-type})))
