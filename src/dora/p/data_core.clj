(ns dora.p.data-core
  (:require [clojure.set :refer :all]
            [clojure.string :as s]
            [mongerr.core :refer :all]
            [dora.p.ckan :refer :all]
            [dora.p.zendesk :refer :all]
            [monger.operators :refer :all]
            [nillib.formats :refer :all]
            [nillib.worm :refer :all]))

(defn dc-add-query [campo value-fn]
  (db-update :data-core
             {:campo campo}
             {:query-en-data-core value-fn}))

(defn dc-update
  ([campo value-fn]
   (try
     (db-update :data-core
                {:campo campo}
                {:value  (if (fn? value-fn)
                           (value-fn)
                           (eval (read-string value-fn)))})
     (catch Exception e (println "caught-exception: dc-update ->>"))))
  ([e]
   (dc-update (:campo e) (:query-en-data-core e)))
  ([]
   (map dc-update  (remove #(nil? (:query-en-data-core %)) (db-find :data-core)))))

(def drive-files {:instituciones "https://docs.google.com/feeds/download/spreadsheets/Export?key=1swzmgetabUT25eog-g6pdgRlc8x9uqz3iCNoruhdnxE&exportFormat=csv&gid=2050308732"
                  :ipda "https://docs.google.com/feeds/download/spreadsheets/Export?key=1swzmgetabUT25eog-g6pdgRlc8x9uqz3iCNoruhdnxE&exportFormat=csv&gid=1077082165"})

(defn parse-drive [m]
  (zipmap (keys m) (map (comp digitalize csv) (vals m))))

(defn drive [] (parse-drive drive-files))

(defn instituciones []
  (let [d (drive)]
    (map #(dissoc % :slug) (join (:instituciones d) (:ipda d) {:siglas :slug}))))

(defn update-db [coll fn]
  (do (db-delete coll)
      (db-insert coll (fn))))

(defn data-core []
  (doall [(update-db :instituciones instituciones)
          (update-db :zendesk-tickets all-tickets)
          (update-db :zendesk-organizations all-organizations)
          (update-db :zendesk-satisfaction all-satisfaction)
          (update-db :zendesk-users all-users)
          (update-db :adela-catalogs adela-catalogs)
          (update-db :adela-plans adela-plans)
          (update-db :adela-organizations adela-organizations)
          (dc-update)]))

(defn metricas
  "Despliega las Métricas de Data Core"
  []
  (map #(vector (:value %) (:campo %))
       (db-find :data-core {:value {$exists true}})))
