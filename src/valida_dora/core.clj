(ns valida-dora.core
  (:require [clojure.java.io :as io]
            [clojure.java.shell :refer :all]
            [clojure.string :as s]
            [nillib.formats :refer :all])
  (:gen-class))

(defn shsh [& command]
  (let [result (sh "/bin/sh" "-c" (s/join " " command))]
    (str (:err result) (:out result))))

(defn ls [dir]
  (re-seq #"[^\n]+" (:out (sh "ls" dir))))

(def metas
  ["head -n 1"
   "file"
   "wc -l"])

(defn profile
  ([file-name] (profile file-name metas))
  ([file-name metas]
    (let [data (slurp file-name)]
      (map #(hash-map :meta (str %1) :data %2)
           metas
           (pmap #(try (if (string? %)
                           (shsh % file-name)
                           (% data))
                  (catch Exception e (str e)))
                 metas)))))

(defn folder-file
  "Concat the file name to the folder"
  [folder file]
  (if-not (= (last folder) \/)
          (str folder "/" file)
          (str folder file)))

(defn is-directory? [route]
  (.isDirectory (io/file route)))

(defn profile-folder [folder]
  (doall (pmap #(try {:file %
                      :profile (profile %)}
                     (catch Exception e nil))
               (map (partial folder-file folder)
                    (remove is-directory? (ls folder))))))

(defn validate [file-name]
  (if (is-directory? file-name)
      (profile-folder file-name)
      (profile file-name)))

(defn -main [file-name]
  (println (json (validate file-name))))
