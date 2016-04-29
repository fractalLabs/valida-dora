(ns dora.pro-file
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]
            [clojure.java.shell :refer :all]
            [clojure.set :refer :all]
            [clojure.string :as s]
            [mongerr.core :refer :all]
            [dora.p.agente-web :refer :all]
            [dora.util :refer :all]
            [nillib.formats :refer :all]
            [nillib.text :refer :all]
            [nillib.worm :refer :all]))

(defn shsh
  "Execute command in shell"
  [& command]
  (let [result (sh "/bin/sh" "-c" (s/join " " command))]
    (str (:err result) (:out result))))

(def metas
  "Vector of 'validations' or scripts whose output we collect"
  ["head -n 1"
   "file"
   "wc -l"
   "validaciones/IDMX/code/prep_proc.sh"])

(defn dora-insert
  "Insert a newly read url into db"
  [url]
  (db-insert :dora {:url url :active true}))

(defn dora-update
  "update results to a validated file"
  [url results]
  (db-update :dora {:url url} {:metadata results :active false}))

(defn dora-find
  [url]
  (db-findf :dora {:url url}))

(defn download-if-url
  "If name is a url, download and return relative path.
  nil if any error"
  [url]
  (try
    (if (re-find url-regex url)
      (let [file-name (str "/tmp/" (rand))
            tmp (spit file-name (GET url))]
        file-name)
      url)
    (catch Exception e)))

(defn execute-validations
  "Execution engine ;)"
  [file-name]
  (map #(hash-map :meta (str %1) :data %2)
       metas
       (pmap #(try                      ;(if (string? %)
                (shsh % file-name)      ;    (% data))
                (catch Exception e (str e)))
             metas)))

(defn format-metadatas [m]
  (identity m)) ;TODO: just a placeholder

(defn broken-link-recommendation [url]
  (if-let [rec (broken-today url)]
    (assoc rec :name "La URL no está disponible"
           :description "Esto puede significar que la URL está caída, o no sea accesible para robots."
           :more-info "http://datos.gob.mx/guia/publica/paso-2-1.html")))

(defn dora-view [result]
  (let [url (:url result)
        resource (db-findf :resources {:url url})
        ] ;todo agregar dataset
    {:resource resource
     :metadata (format-metadatas (apply merge
                                        resource
                                        (map #(hash-map (:meta %) (:data %))
                                             (:metadata result))))
     :recomendations (remove-nils [() (broken-today url)])}))

(defn profile
  "if first time, run validations and store.
  For returning costumers return previous results"
  ([url] (profile url metas))
  ([url metas]
   (if-let [result (dora-find url)]
     (dora-view result)
     (let [tmp (dora-insert url)
           file-name (download-if-url url)
           results (execute-validations file-name)]
         (dora-update url results)
         (dora-view (dora-find url))))))

(defn folder-file
  "Concat the file name to the folder"
  [folder file]
  (if-not (= (last folder) \/)
          (str folder "/" file)
          (str folder file)))

(defn profile-folder
  "Run validations on all files from folder"
  [folder]
  (doall (pmap #(try (db-insert :validadora
                                {:file %
                                 :profile (profile %)})
                     (catch Exception e (db-insert :error
                                                   {:error (str e)})))
               (map (partial folder-file folder)
                    (remove is-directory? (ls folder))))))

(defn validate
  "Validate a file.
  Main entry point."
  [file-name]
  (if (map? file-name)
    (validate (:url file-name))
    (if (is-directory? file-name)
      (profile-folder file-name)
      (profile file-name))))

(defn main [file-name]
  (println (json (validate file-name))))




;; Scrapbook

(defn save-profiles [folder]
  (doall (pmap #(try (db-upsert :file-profiles
                                {:file %}
                                {:profile (profile %) :file %})
                     (catch Exception e (db-insert :error
                                                   {:error (str e)})))
               (map (partial folder-file folder)
                    (remove is-directory? (ls folder))))))

(defn pa-arriba [m k]
  (dissoc (apply merge m (m k)) k))

(defn apply-to-vals [f m]
  (zipmap (keys m) (map f (vals m))))
(defn trim-vals [m]
  (zipmap (keys m) (map s/trim (vals m))))

(defn remove-st-err [s]
  (last (re-seq #"[^\n]+" s)))

(defn format-profile [profile]
  (let [o (dissoc profile :profile)
        p (zipmap (map :meta (:profile profile))
                  (map :data (:profile profile)))
        p (apply merge p (:metadata (json (remove-st-err (p "validaciones/IDMX/code/prep_proc.sh")))))
        p (dissoc p "validaciones/IDMX/code/prep_proc.sh" "head -n 1")
        p (pa-arriba p :size)
        p (pa-arriba p :aditional_info)
        p (pa-arriba p :encoding)
        p (assoc (apply-to-vals #(remove-str % (:file_name p)) p) :id (remove-str (:file_name p) "../../resources/"))]
    (trim-vals p)))

;remove strings identicas al file name:

(defn db-validadora [] (map format-profile (db-find :validadora)))

(defn join-resources []
  (let [v (db-validadora)
        r (db-find :resources)]
    (join v r {:id :id})))


                                        ;estudio
(defn conteo-por-llave [ms]
  (zipmap (keys (first ms)) (map #(count (distinct (map % (json (json ms))))) (keys (first ms)))))

(defn frequencies-peek [v]
  (take 20 (sorted-frequencies v)))


(defn frequencies-peek [v]
  (take 20 (sorted-frequencies v)))

(defn recomendaciones [url]
  (:recomendaciones (db-find :dora {:url url})))
