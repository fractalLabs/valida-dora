(ns dora.twitter
  (:require [clojure.java.shell :refer :all]
            [mongerr.core :refer :all]
            [dora.util :refer :all]
            [nillib.formats :refer :all]
            [nillib.worm :refer :all]
            [clj-time.core :as t]))

;; installation:
; gem install t

(defn split [splitter s]
  (re-seq (re-pattern (str "[^" splitter "]+"))
          s))

(defn trends
  ([] (trends "23424900")) ;default to mexico
  ([woeid] (split "\n"
                  (:out (sh "t" "trends" woeid)))))

(defn trends-fetch
  ([woeid]
   (db-insert :trends {:woeid woeid
                       :trends (trends woeid)}))
  ([] (trends-fetch "23424900")))

(defn sht [& args]
  (:out (apply sh "t" args)))

(defn shtsearch
 [q]
 (sht "search" "all" q "--csv" "-d" "-n" 80000000))

(def palabras-monitoreadas
  ["datosabiertos" "datos.gob.mx" "gob.mx"])

(defn accounts
  []
  (sort-by :followers >
           (distinct-by :screen-name
                        (digitalize
                         (mapcat csv
                                 (ls-fr "resources/mkt/t/followers"))))))


(defn db-trends []
  (distinct (map :trends (db-find :trends))))

(defn trends-analysis []
  (sort-by second > (frequencies (flatten (db-trends)))))
