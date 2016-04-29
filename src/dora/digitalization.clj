(ns dora.digitalization
  "Conección a BD"
  (:require [clj-time.format :as f]
            [clj-time.core :as t]
            [mongerr.core :refer :all]
            [dora.util :refer :all]
            [monger.collection :as mc]
            [monger.command :as cmd]
            [monger.core :as mg]
            [monger.db :refer [get-collection-names]]
            monger.joda-time
            [monger.operators :refer :all]
            [nillib.tipo :refer :all]
            [nillib.worm :refer :all]))

(defn date-tokens [s]
  (re-seq #""))

(defn char-type-regex [s]
  (if (re-find #"\d" s)
    "\\d"
    (if (re-find #"[a-zA-z]" s)
      "[a-zA-Z]"
      s)))

(defn date-regex [s]
  (re-pattern (apply str (map #(char-type-regex (str %)) s))))

(def formatters [{:formatter :hour-minute, :sample "03:44", :regex #"\d\d:\d\d"} {:formatter :t-time, :sample "T03:44:20.123Z", :regex #"[a-zA-Z]\d\d:\d\d:\d\d.\d\d\d[a-zA-Z]"} {:formatter :basic-ordinal-date, :sample "2016108", :regex #"\d\d\d\d\d\d\d"} {:formatter :date, :sample "2016-04-17", :regex #"\d\d\d\d-\d\d-\d\d"} {:formatter :hour, :sample "03", :regex #"\d\d"} {:formatter :time-no-ms, :sample "03:44:20Z", :regex #"\d\d:\d\d:\d\d[a-zA-Z]"} {:formatter :weekyear-week-day, :sample "2016-W15-7", :regex #"\d\d\d\d-[a-zA-Z]\d\d-\d"} {:formatter :week-date-time, :sample "2016-W15-7T03:44:20.123Z", :regex #"\d\d\d\d-[a-zA-Z]\d\d-\d[a-zA-Z]\d\d:\d\d:\d\d.\d\d\d[a-zA-Z]"} {:formatter :date-hour-minute-second-fraction, :sample "2016-04-17T03:44:20.123", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d:\d\d:\d\d.\d\d\d"} {:formatter :basic-date-time, :sample "20160417T034420.123Z", :regex #"\d\d\d\d\d\d\d\d[a-zA-Z]\d\d\d\d\d\d.\d\d\d[a-zA-Z]"} {:formatter :date-time, :sample "2016-04-17T03:44:20.123Z", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d:\d\d:\d\d.\d\d\d[a-zA-Z]"} {:formatter :basic-time-no-ms, :sample "034420Z", :regex #"\d\d\d\d\d\d[a-zA-Z]"} {:formatter :basic-week-date, :sample "2016W157", :regex #"\d\d\d\d[a-zA-Z]\d\d\d"} {:formatter :basic-t-time-no-ms, :sample "T034420Z", :regex #"[a-zA-Z]\d\d\d\d\d\d[a-zA-Z]"} {:formatter :date-time-no-ms, :sample "2016-04-17T03:44:20Z", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d:\d\d:\d\d[a-zA-Z]"} {:formatter :year-month-day, :sample "2016-04-17", :regex #"\d\d\d\d-\d\d-\d\d"} {:formatter :rfc822, :sample "Sun, 17 Apr 2016 03:44:20 +0000", :regex #"[a-zA-Z][a-zA-Z][a-zA-Z], \d\d [a-zA-Z][a-zA-Z][a-zA-Z] \d\d\d\d \d\d:\d\d:\d\d +\d\d\d\d"} {:formatter :date-hour-minute-second-ms, :sample "2016-04-17T03:44:20.123", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d:\d\d:\d\d.\d\d\d"} {:formatter :basic-ordinal-date-time, :sample "2016108T034420.123Z", :regex #"\d\d\d\d\d\d\d[a-zA-Z]\d\d\d\d\d\d.\d\d\d[a-zA-Z]"} {:formatter :ordinal-date, :sample "2016-108", :regex #"\d\d\d\d-\d\d\d"} {:formatter :hour-minute-second-fraction, :sample "03:44:20.123", :regex #"\d\d:\d\d:\d\d.\d\d\d"} {:formatter :date-hour-minute, :sample "2016-04-17T03:44", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d:\d\d"} {:formatter :time, :sample "03:44:20.123Z", :regex #"\d\d:\d\d:\d\d.\d\d\d[a-zA-Z]"} {:formatter :basic-week-date-time, :sample "2016W157T034420.123Z", :regex #"\d\d\d\d[a-zA-Z]\d\d\d[a-zA-Z]\d\d\d\d\d\d.\d\d\d[a-zA-Z]"} {:formatter :weekyear, :sample "2016", :regex #"\d\d\d\d"} {:formatter :basic-time, :sample "034420.123Z", :regex #"\d\d\d\d\d\d.\d\d\d[a-zA-Z]"} {:formatter :hour-minute-second, :sample "03:44:20", :regex #"\d\d:\d\d:\d\d"} {:formatter :ordinal-date-time, :sample "2016-108T03:44:20.123Z", :regex #"\d\d\d\d-\d\d\d[a-zA-Z]\d\d:\d\d:\d\d.\d\d\d[a-zA-Z]"} {:formatter :ordinal-date-time-no-ms, :sample "2016-108T03:44:20Z", :regex #"\d\d\d\d-\d\d\d[a-zA-Z]\d\d:\d\d:\d\d[a-zA-Z]"} {:formatter :hour-minute-second-ms, :sample "03:44:20.123", :regex #"\d\d:\d\d:\d\d.\d\d\d"} {:formatter :year, :sample "2016", :regex #"\d\d\d\d"} {:formatter :t-time-no-ms, :sample "T03:44:20Z", :regex #"[a-zA-Z]\d\d:\d\d:\d\d[a-zA-Z]"} {:formatter :basic-week-date-time-no-ms, :sample "2016W157T034420Z", :regex #"\d\d\d\d[a-zA-Z]\d\d\d[a-zA-Z]\d\d\d\d\d\d[a-zA-Z]"} {:formatter :basic-date, :sample "20160417", :regex #"\d\d\d\d\d\d\d\d"} {:formatter :weekyear-week, :sample "2016-W15", :regex #"\d\d\d\d-[a-zA-Z]\d\d"} {:formatter :basic-ordinal-date-time-no-ms, :sample "2016108T034420Z", :regex #"\d\d\d\d\d\d\d[a-zA-Z]\d\d\d\d\d\d[a-zA-Z]"} {:formatter :year-month, :sample "2016-04", :regex #"\d\d\d\d-\d\d"} {:formatter :week-date, :sample "2016-W15-7", :regex #"\d\d\d\d-[a-zA-Z]\d\d-\d"} {:formatter :date-hour, :sample "2016-04-17T03", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d"} {:formatter :date-hour-minute-second, :sample "2016-04-17T03:44:20", :regex #"\d\d\d\d-\d\d-\d\d[a-zA-Z]\d\d:\d\d:\d\d"} {:formatter :week-date-time-no-ms, :sample "2016-W15-7T03:44:20Z", :regex #"\d\d\d\d-[a-zA-Z]\d\d-\d[a-zA-Z]\d\d:\d\d:\d\d[a-zA-Z]"} {:formatter :basic-date-time-no-ms, :sample "20160417T034420Z", :regex #"\d\d\d\d\d\d\d\d[a-zA-Z]\d\d\d\d\d\d[a-zA-Z]"} {:formatter :mysql, :sample "2016-04-17 03:44:20", :regex #"\d\d\d\d-\d\d-\d\d \d\d:\d\d:\d\d"} {:formatter :basic-t-time, :sample "T034420.123Z", :regex #"[a-zA-Z]\d\d\d\d\d\d.\d\d\d[a-zA-Z]"}])

(defn date-checker
  "Check if this is consistent with a date"
  [s]
  (remove-nils (map #(re-find (:regex %) s) (filter #(= (count s) (count (:sample %))) formatters))))

(defn date-parse [s]
  (let [type (date-checker s)
        unkn (empty? type)]
    (if unkn s
        (f/parse (:formatter (first type)) s))))
