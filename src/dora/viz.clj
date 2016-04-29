(ns dora.viz
  (:require [nillib.formats :refer :all]))

(defn cloud [vecs]
  (map #(zipmap [:text :weight] %) vecs))

;(def a (csv "/Users/nex/Documents/pri/top-trends-02-22.csv"))
;(def b (cloud (map #(vector (:hashtag %) (:count %)) a)))
;(json b)

(defn single-line [vecs]
  {:labels (map first vecs)
   :data (map second vecs)})
