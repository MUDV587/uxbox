;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016-2019 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.common.spec
  (:require
   [clojure.spec.alpha :as s]
   [cuerdas.core :as str]
   #?(:clj [datoteka.core :as fs])))

(s/check-asserts true)

;; --- Constants

(def email-rx
  #"^[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+$")

(def uuid-rx
  #"^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")

(def number-rx
  #"^[+-]?([0-9]*\.?[0-9]+|[0-9]+\.?[0-9]*)([eE][+-]?[0-9]+)?$")

;; --- Predicates

(defn email?
  [v]
  (and (string? v)
       (re-matches email-rx v)))

;; --- Conformers

(defn- uuid-conformer
  [v]
  (cond
    (uuid? v) v
    (string? v)
    (cond
      (re-matches uuid-rx v)
      #?(:clj (java.util.UUID/fromString v)
         :cljs (uuid v))

      (str/empty? v)
      nil

      :else
      ::s/invalid)
    :else ::s/invalid))

(defn- integer-conformer
  [v]
  (cond
    (integer? v) v
    (string? v)
    (if (re-matches #"^[-+]?\d+$" v)
      (Long/parseLong v)
      ::s/invalid)
    :else ::s/invalid))

(defn boolean-conformer
  [v]
  (cond
    (boolean? v) v
    (string? v)
    (if (re-matches #"^(?:t|true|false|f|0|1)$" v)
      (contains? #{"t" "true" "1"} v)
      ::s/invalid)
    :else ::s/invalid))

(defn boolean-unformer
  [v]
  (if v "true" "false"))

#?(:clj
   (defn path-conformer
     [v]
     (cond
       (string? v) (fs/path v)
       (fs/path? v) v
       :else ::s/invalid)))

(defn- number-conformer
  [v]
  (cond
    (number? v) v

    (str/numeric? v)
    #?(:clj (Double/parseDouble v)
       :cljs (js/parseFloat v))

    :else ::s/invalid))

;; --- Default Specs

(s/def ::string string?)
(s/def ::integer (s/conformer integer-conformer str))
(s/def ::uuid (s/conformer uuid-conformer str))
(s/def ::boolean (s/conformer boolean-conformer boolean-unformer))
(s/def ::number (s/conformer number-conformer str))

(s/def ::inst inst?)
(s/def ::positive pos?)
(s/def ::negative neg?)
(s/def ::uploaded-file any?)
(s/def ::email email?)
(s/def ::file any?)

;; Clojure Specific
#?(:clj
   (do
     (s/def ::bytes bytes?)
     (s/def ::name ::string)
     (s/def ::size ::integer)
     (s/def ::mtype ::string)
     (s/def ::path (s/conformer path-conformer str))
     (s/def ::upload
       (s/keys :req-un [::name ::path ::size ::mtype]))))
