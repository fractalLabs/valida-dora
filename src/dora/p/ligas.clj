(ns dora.p.ligas
  (:require [clj-http.client :as http]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clojure.string :as s]
            [monger.operators :refer :all]
            monger.joda-time)
  (:use dora.p.zendesk
        mongerr.core
        nillib.formats
        ring.util.codec)
  (:gen-class))

(defn ligas-caidas
  ([] (db-find "ligas-caidas"))
  ([day] (db-find "ligas-caidas" {:time {$gte (t/date-time (t/year day)
                                                           (t/month day)
                                                           (t/day day))}})))

(defn separa-urls [dia]
    (map #(hash-map :time (:time dia) :url %) ;#(assoc % :time (:time dia))
         (distinct (:urls dia))))

(defn ligas-repetidas [ligas]
  (sort-by second > (filter #(> (second %) 1)
                            (frequencies (map :url (mapcat separa-urls
                                                      ligas))))))

;(ligas-repetidas (ligas-caidas desde-dia))

(defn ligas-repetidas-3 []
  (map first (filter #(< 2 (second %)) (ligas-repetidas (take-last 3 (sort-by :time (ligas-caidas)))))))

(defn caidas-por-dependencia []
  (let [lr (ligas-repetidas-3)]
    (map #(hash-map :organization %
                    :urls (map :url (filter (fn [entry] (= % (:organization entry))) lr)))
         (distinct (map :organization lr)))))
