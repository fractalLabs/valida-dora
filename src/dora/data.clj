(ns dora.data
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as sh]
            [clojure.string :as s]
            [clojure.zip :as zip]
            [mongerr.core :refer :all]
            [dora.util :refer :all]
            [nillib.formats :refer :all]
            [nillib.worm :refer :all]
            [org.httpkit.client :as http]))

(defn copy
  "Download 'url' with name 'file'"
  [url file]
  (println "downloading: " url)
  (try
    (io/copy
     (:body @(http/get url {:as :stream}))
     (java.io.File. file))
    (catch Exception e (spit file (slurp url))))
  (println "finished with: " url))

(defn directory [& s]
  (apply str "/tmp/" s))

(defn data-directory
  [s]
  (str "/var/lib/jenkins/resources/" s))

(defn slurp-csv [url]
  (println "preparing to import: " url)
  (map #(assoc % :url url)
       (digitalize (csv url))))

(defn name-csv? [url]
  (re-find #"csv|CSV" url))

(defn import-csv
  ([url]
   (import-csv url {}))
  ([url metadata]
   (db-insert :data (map #(merge % metadata)
                         (slurp-csv url)))))

(defn unzip
  [file]
  (let [newname (last (re-seq #"[^/]+" file))]
    (shs "mkdir" "/tmp/zip")
    (shs "mkdir" (directory "zip/" newname))
    (shs "unzip" file "-d" (str "/tmp/zip/" newname "/"))
   ; (shs "rm" file)
    (str "/tmp/zip/" newname)))

(defn trash-file? [s]
  (re-find #"__MACOSX|tmp|bak" s))

(defn unzip-l
  "Peek into the files in the zipfile"
  [file] ;TODO limpiar mas los metadatos
  (try
    (let [data (split-lines (shs "unzip" "-l" file))
          rows (drop-last 2 (nthrest data 3))]
      (remove trash-file?
              (remove ends-in-dash?
                      (map #(s/trim (remove-str % #"[0-9]+  [0-9\-: ]+   "))
                           rows))))
    (catch Exception e (print file e))))

(defn import-file
  ([url] (import-file url {}))
  ([url metadata]
   (println "importing: " url)
   (let [tmpfile (str (rand))
         tmpdir (directory tmpfile)]
     (copy url tmpdir)
     (cond
       (re-find #"zip|ZIP" url) (do (unzip tmpdir)
                                    (map #(import-csv % metadata)
                                         (map #(str "/tmp/zip/" tmpfile "/" %)
                                              (unzip-l tmpdir))))
       (re-find #"csv|CSV" url) (import-csv tmpdir metadata)))))

(defn import-resource
  [resource]
  (let [id (:id resource)
        dir (data-directory id)
        url (:url resource)
        metadata {:id (:id resource)
                  :name (:name resource)}]
    (try
      (cond
        (re-find #"zip|ZIP" url) (do (unzip dir)
                                     (map #(import-csv % metadata)
                                          (map #(str "/tmp/zip/" dir "/" %)
                                               (unzip-l dir))))
        (re-find #"csv|CSV" url) (import-csv dir metadata))
      (db-update :resources {:id id} {:imported true})
      (catch Exception e (println "ERROR IMPORTING: " id "\n error is: " e)))))
