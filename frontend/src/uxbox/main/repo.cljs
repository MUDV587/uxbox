;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns uxbox.main.repo
  (:require
   [beicon.core :as rx]
   [cuerdas.core :as str]
   [uxbox.config :as cfg]
   [uxbox.util.http-api :as http]))

(defn- handle-response
  [response]
  (cond
    (http/success? response)
    (rx/of (:body response))

    (http/client-error? response)
    (rx/throw (:body response))

    :else
    (rx/throw {:type :unexpected
               :code (:error response)})))

(defn send-query!
  [id params]
  (let [uri (str cfg/public-uri "/api/w/query/" (name id))]
    (->> (http/send! {:method :get :uri uri :query params})
         (rx/mapcat handle-response))))

(defn send-mutation!
  [id params]
  (let [uri (str cfg/public-uri "/api/w/mutation/" (name id))]
    (->> (http/send! {:method :post :uri uri :body params})
         (rx/mapcat handle-response))))

(defn- dispatch
  [& args]
  (first args))

(defmulti query dispatch)
(defmulti mutation dispatch)

(defmethod query :default
  [id params]
  (send-query! id params))

(defmethod mutation :default
  [id params]
  (send-mutation! id params))

(defn query!
  ([id] (query id {}))
  ([id params] (query id params)))

(defn mutation!
  ([id] (mutation id {}))
  ([id params] (mutation id params)))

(defmethod mutation :login-with-google
  [id params]
  (let [uri (str cfg/public-uri "/api/oauth/google")]
    (->> (http/send! {:method :post :uri uri})
         (rx/mapcat handle-response))))

(defmethod mutation :upload-media-object
  [id params]
  (let [form (js/FormData.)]
    (run! (fn [[key val]]
            (.append form (name key) val))
          (seq params))
    (send-mutation! id form)))

;; (defmethod mutation :upload-file-image
;;   [id params]
;;   (let [form (js/FormData.)]
;;     (run! (fn [[key val]]
;;             (.append form (name key) val))
;;           (seq params))
;;     (send-mutation! id form)))

(defmethod mutation :update-profile-photo
  [id params]
  (let [form (js/FormData.)]
    (run! (fn [[key val]]
            (.append form (name key) val))
          (seq params))
    (send-mutation! id form)))

(defmethod mutation :login
  [id params]
  (let [uri (str cfg/public-uri "/api/login")]
    (->> (http/send! {:method :post :uri uri :body params})
         (rx/mapcat handle-response))))

(defmethod mutation :logout
  [id params]
  (let [uri (str cfg/public-uri "/api/logout")]
    (->> (http/send! {:method :post :uri uri :body params})
         (rx/mapcat handle-response))))

(def client-error? http/client-error?)
(def server-error? http/server-error?)
