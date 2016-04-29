(ns dora.p.download
  (:use dora.p.ckan
        clojure.java.shell
        nillib.formats
        nillib.worm)
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [dora.data :refer :all]
            [mongerr.core :refer :all]
            [dora.util :refer :all]
            [monger.operators :refer :all]
            [org.httpkit.client :as http]))

(defn tmpdir
  [& ss]
  (apply str "/tmp/" ss))

(defn recurso->file [recurso]
  (data-directory (:id recurso)))

(defn recursosdb []
  (db-find "resources"))

(defn recursos-descargados []
  (ls (data-directory "")))

(defn recursos-faltantes
  ([] (recursos-faltantes (recursosdb)))
  ([universe]
   (let [ya (recursos-descargados)]
     (remove #(some (set [(:id %)])
                    ya)
             universe))))

(defn quita-con-pedos
  "quita los recursos que tienen pedos"
  [recursos]
  (let [problemic (map :id (db :resources-rotos))]
    (remove #(some (set [(:id %)])
                   problemic)
            recursos)))

(defn list-recursos-faltantes-pemex []
  (csv "/tmp/pemex.csv"
       (recursos-faltantes
        (mapcat :resources
                (filter #(= "pemex" (:name (:organization %)))
                        (db-find :datasets_bak))))))

(defn fix-google-url
  [url]
  (if (re-find #"https://drive.google.com/file/d" url)
      (let [id (nth (re-seq #"[^/]+" url) 4)]
        (str "https://drive.google.com/uc?export=download&id=" id))
      url))

(defn fix-google-urls [recursos]
  (map #(assoc % :url (fix-google-url (:url %))) recursos))

(defn resources-to-download
  []
  (fix-google-urls (doall (quita-con-pedos (recursos-faltantes)))))

(defn grab-exception-handler
  [m e]
  (println "p2 con url: " (:url m) ", la excepcion fue: " e ". saved: " (db-insert :resources-rotos (assoc m :exception (str e)))))

(defn grab ;;TODO
  "Agarra un recurso y trata de descargarlo"
  [m]
  (try (copy (:url m) (data-directory (:id m)))
       (catch Exception e (grab-exception-handler m e))))

(defn download
  ([] (download (resources-to-download)))
  ([recursos]
    (doall (pmap grab recursos))))

(defn download-rand []
  (download (shuffle (resources-to-download))))

(defn import-resources []
  (doall (pmap import-resource
               (db-find "resources" {$or [{:imported {$exists false}}
                                          {:imported false}]}))))
