(ns dora.validations
  (:require [clojure.string :as s]
            [mongerr.core :refer [db-find db-update]]
            [dora.data :refer :all]
            [dora.p.download :refer [recurso->file
                                  recursos-descargados tmpdir]]
            [dora.pro-file :refer [shsh]]
            [dora.util :refer :all]
            [nillib.worm :refer [digitalize]]))

(defn file
	  "apply shell function file to 'name'"
	  [name]
    (remove-str (shsh "file" name) (str name ": ")))

(defn assoc-file [recurso]
  (assoc recurso :file (file (recurso->file recurso))))

(defn separate-lines [s]
  (re-seq #"[^\n]+" s))

(defn unzipl
  "Peek into the files in the zipfile"
  [file] ;TODO limpiar mas los metadatos
  (try
    (let [data (separate-lines (shs "unzip" "-l" file))
          rows (drop-last 2 (nthrest data 3))]
      (remove ends-in-dash? (map #(s/trim (remove-str % #"[0-9]+  [0-9\-: ]+   ")) rows)))
    (catch Exception e (print file e))))

(defn filehead [file]
  (re-find #"[^\n]+" (slurp file)))

(defn cols
  ([id] (cols id (data-directory id)))
  ([id file]
    (let [data (re-seq #"[^,|]+"
                      (filehead file))]
       (map #(clojure.string/trim (remove-str % "\""))  data))))

(defn assoc-unzipped-files
  [recurso]
  (assoc recurso :files (unzipl (recurso->file recurso))))

(defn inspect-unzipped-files [recurso]
  (let [files (:files recurso)]
    (assoc recurso :files (map #(hash-map :name %
                                          :file (file (tmpdir (:id recurso) "/" %))
                                          :head (cols (:id recurso) (tmpdir (:id recurso) "/" %)))
                                files))))

(defn recurso-CSV [recurso]
  (assoc recurso :cols (cols (:id recurso))))

(defn assoc-head [recurso]
  (assoc recurso :head (cols (:id recurso))))

(defn recurso-ZIP [recurso]
  (if (re-find #"Zip" (:file recurso)) ;if zip
      (do
        (unzip (directory (:id recurso)))
        (inspect-unzipped-files (dissoc (assoc-unzipped-files recurso) :head)))
      recurso))


(defn assoc-format [recurso]
  (if (nil? (:type recurso))
    (let [url (:url recurso)]
      (cond
        (re-seq #"csv" url) (assoc recurso :format "CSV")
        (re-seq #"xls" url) (assoc recurso :format "xlsx")
        (re-seq #"zip" url) (assoc recurso :format "ZIP")
        :else recurso))
      recurso))

(defn process-recurso
  "extract and store metadata from the resource"
  [recurso]
  (-> recurso
      assoc-file
      assoc-head
      assoc-format
      recurso-ZIP
      digitalize))

(defn process-and-update [recurso]
  (try (db-update :resources {:id (:id recurso)} (process-recurso recurso))
    (catch Exception e (spit "p2" (str (:id recurso) "\n") :append true))))

(comment
(def defrecursos (db-find :resources))
(defn id->recurso [id]
  (let [match (filter #(= id (:id %)) defrecursos)]
    (if match (first match))))

(defn process-recursos
  "save to mongo all extracted metadata"
  []
  (pmap #(process-and-update (id->recurso %)) (recursos-descargados)))

(defn process-recursos-coll
  "return a coll with extracted metadata"
  []
  (pmap #(process-recurso (id->recurso %)) (recursos-descargados)))
)


;(def a (recursosdb))
;(defn the-knife [k v]
;  (filter #(= v (k %))
;          a))
