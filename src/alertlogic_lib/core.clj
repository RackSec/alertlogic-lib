(ns alertlogic-lib.core
  (:require
   [clojure.string :as str]
   [base64-clj.core :as base64]))

(def base-url "https://api.alertlogic.net")

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
