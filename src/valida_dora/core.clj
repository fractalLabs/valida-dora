(ns valida-dora.core
  (:require [clojure.java
             [io :as io]
             [shell :refer :all]]
            [clojure.string :as s]
            [nillib.formats :refer :all])
  (:gen-class))

(defn shsh
  "Ejecuta un comando de bash, los argumentos pueden estar
  en una o mas strings"
  [& command]
  (let [result (sh "/bin/sh" "-c" (s/join " " command))]
    (str (:err result) (:out result))))

(defn ls
  "Wrapper de 'ls' de bash"
  [dir]
  (re-seq #"[^\n]+" (:out (sh "ls" dir))))

(def metas
  "Vector con las validaciones a realizar"
  ["head -n 1"
   "file"
   "wc -l"
   "validaciones/IDMX/code/prep_proc.sh"])

(defn profile
  "Hacer las perfilaciones para un archivo"
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
  "Concatena la ruta al archivo"
  [folder file]
  (if-not (= (last folder) \/)
    (str folder "/" file)
    (str folder file)))

(defn is-directory?
  "Predicado para checar si el archivo es directorio"
  [route]
  (.isDirectory (io/file route)))

(defn profile-folder
  "Hacer las perfilaciones para todos los archivos de un folder"
  [folder]
  (doall (pmap #(try {:file %
                      :profile (profile %)}
                     (catch Exception e nil))
               (map (partial folder-file folder)
                    (remove is-directory? (ls folder))))))

(defn validate
  "Ejecuta las perfilaciones para un archivo o folder"
  [file-name]
  (if (is-directory? file-name)
    (profile-folder file-name)
    (profile file-name)))

(defn -main
  "Ejecuta las validaciones e imprime el resultado en json"
  [file-name]
  (println (json (validate file-name))))
