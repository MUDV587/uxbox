;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.db
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [lambdaisland.uri :refer [uri]]
   [mount.core :as mount :refer [defstate]]
   [next.jdbc :as jdbc]
   [next.jdbc.date-time :as jdbc-dt]
   [next.jdbc.optional :as jdbc-opt]
   [next.jdbc.result-set :as jdbc-rs]
   [next.jdbc.sql :as jdbc-sql]
   [next.jdbc.sql.builder :as jdbc-bld]
   [uxbox.metrics :as mtx]
   [uxbox.common.exceptions :as ex]
   [uxbox.config :as cfg]
   [uxbox.util.data :as data])
  (:import
   org.postgresql.util.PGobject
   com.zaxxer.hikari.metrics.prometheus.PrometheusMetricsTrackerFactory
   com.zaxxer.hikari.HikariConfig
   com.zaxxer.hikari.HikariDataSource))

(defn- create-datasource-config
  [cfg]
  (let [dburi    (:database-uri cfg)
        username (:database-username cfg)
        password (:database-password cfg)
        config (HikariConfig.)
        mfactory (PrometheusMetricsTrackerFactory. mtx/registry)]
    (doto config
      (.setJdbcUrl (str "jdbc:" dburi))
      (.setPoolName "main")
      (.setAutoCommit true)
      (.setReadOnly false)
      (.setConnectionTimeout 30000) ;; 30seg
      (.setValidationTimeout 5000)  ;; 5seg
      (.setIdleTimeout 900000)      ;; 15min
      (.setMaxLifetime 900000)      ;; 15min
      (.setMinimumIdle 5)
      (.setMaximumPoolSize 10)
      (.setMetricsTrackerFactory mfactory))
    (when username (.setUsername config username))
    (when password (.setPassword config password))
    config))

(defn- create-pool
  [cfg]
  (let [dsc (create-datasource-config cfg)]
    (jdbc-dt/read-as-instant)
    (HikariDataSource. dsc)))

(defstate pool
  :start (create-pool cfg/config)
  :stop (.close pool))

(defmacro with-atomic
  [& args]
  `(jdbc/with-transaction ~@args))

(defn- kebab-case [s] (str/replace s #"_" "-"))
(defn- snake-case [s] (str/replace s #"-" "_"))
(defn- as-kebab-maps
  [rs opts]
  (jdbc-opt/as-unqualified-modified-maps rs (assoc opts :label-fn kebab-case)))

(defn open
  []
  (jdbc/get-connection pool))

(defn exec!
  ([ds sv]
   (exec! ds sv {}))
  ([ds sv opts]
   (jdbc/execute! ds sv (assoc opts :builder-fn as-kebab-maps))))

(defn exec-one!
  ([ds sv] (exec-one! ds sv {}))
  ([ds sv opts]
   (jdbc/execute-one! ds sv (assoc opts :builder-fn as-kebab-maps))))

(def ^:private default-options
  {:table-fn snake-case
   :column-fn snake-case
   :builder-fn as-kebab-maps})

(defn insert!
  [ds table params]
  (jdbc-sql/insert! ds table params default-options))

(defn update!
  [ds table params where]
  (let [opts (assoc default-options :return-keys true)]
    (jdbc-sql/update! ds table params where opts)))

(defn delete!
  [ds table params]
  (let [opts (assoc default-options :return-keys true)]
    (jdbc-sql/delete! ds table params opts)))

(defn get-by-params
  ([ds table params]
   (get-by-params ds table params nil))
  ([ds table params opts]
   (let [opts (cond-> (merge default-options opts)
                (:for-update opts)
                (assoc :suffix "for update"))
         res  (exec-one! ds (jdbc-bld/for-query table params opts) opts)]
     (when (:deleted-at res)
       (ex/raise :type :not-found))
     res)))

(defn get-by-id
  ([ds table id]
   (get-by-params ds table {:id id} nil))
  ([ds table id opts]
   (get-by-params ds table {:id id} opts)))

(defn query
  ([ds table params]
   (query ds table params nil))
  ([ds table params opts]
   (let [opts (cond-> (merge default-options opts)
                (:for-update opts)
                (assoc :suffix "for update"))]
     (exec! ds (jdbc-bld/for-query table params opts) opts))))

(defn pgobject?
  [v]
  (instance? PGobject v))

(defn decode-pgobject
  [^PGobject obj]
  (let [typ (.getType obj)
        val (.getValue obj)]
    (if (or (= typ "json")
            (= typ "jsonb"))
      (json/read-str val)
      val)))

;; Instrumentation

(mtx/instrument-with-counter!
 {:var [#'jdbc/execute-one!
        #'jdbc/execute!]
  :id "database__query_counter"
  :help "An absolute counter of database queries."})
