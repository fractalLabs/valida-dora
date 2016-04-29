(ns dora.p.ckan
  (:use clj-http.util
        nillib.formats
        nillib.worm)
  (:require [clj-http.client :as http]
            [clojure.data.json :as json]
            [clojure.string :as s]
            [mongerr.core :refer :all]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            monger.joda-time)
  (:import [com.mongodb MongoOptions ServerAddress]))

(defn get-json [url]
  (-> url
      http/get
      :body
      (json/read-str :key-fn (comp keyword #(clojure.string/replace % " " "_")))))

(defn api [& endpoint]
  (:result (get-json (str "http://catalogo.datos.gob.mx/api/3/"
                          (s/join endpoint)))))

(defn package-list []
  (api "action/package_list"))

(defn package-show
  ([] (pmap package-show (package-list)))
  ([package]
    (api "action/package_show?id=" package)))

(defn group-list []
  (api "action/group_list"))

(defn group-show [id]
  (api "action/group_show?id=" id))

(defn tag-list []
  (api "action/tag_list"))

(defn tag-show [id]
  (api "action/tag_show?id=" id))

(defn package-search [q]
  (api "action/package_search?q=" q))

(defn resource-search [q]
  (api "action/resource_search?query=" q))

(defn recently-changed []
  (api "action/recently_changed_packages_activity_list"))

(defn all-ckan []
  (digitalize (map package-show (package-list))))

(defn email [dataset]
  (let [extras (:extras dataset)
        email (filter #(= "dcat_publisher_email" (:key %)) extras)]
    (if-not (empty? email)
      (re-seq #"[^,; \n]+"
              (:value (first email))))))

(defn emails [datasets]
  (remove nil? (distinct (flatten (map email datasets)))))

(defn urls [datasets]
  (map #(map :url (:resources %)) datasets))

(defn ckan-organizations [datasets]
  (distinct (map :organization datasets)))

(defn recursos-from-datasets [dataset]
  (map #(assoc % :dataset_id (:id dataset)
                 :organization (:name (:organization dataset)))
      (:resources dataset)))

(defn update-all-ckan [];;;p2:consistencia, bench:6.2m
  (let [data (all-ckan)
        ndata (count data)]
    (db-delete :datasets)
    (db-delete :resources)
    (println "updated " (count (map #(db-insert "datasets" %) data)) "datasets")
    (println "updated "
         (count (map #(db-insert "resources" %)
                     (mapcat recursos-from-datasets data)))
         " resources")))

(def adela-url "http://adela.datos.gob.mx/")

(defn adela-api [& endpoint]
  (get-json (str adela-url
                 (s/join endpoint))))

(defn adela-catalog [slug]
  (adela-api slug "/catalogo.json"))

(defn adela-plan [slug]
  (adela-api slug "/plan.json"))

(defn adela-catalogs []
  (map (comp adela-catalog :slug) (adela-api "api/v1/catalogs")))

(defn adela-plans []
  (remove nil? (map (comp adela-plan :slug) (adela-api "api/v1/catalogs"))))

(defn organizations-req
  ([] (organizations-req 1))
  ([page] (adela-api "api/v1/organizations?page=" page)))

(defn adela-organizations []
  (let [r1 (organizations-req)
        p (:pagination r1)
        last-page (int (Math/ceil (/ (:total p) (:per_page p))))]
    (apply concat (:results r1) (map #(:results (organizations-req %))
                                     (drop 2 (range (inc last-page)))))))
