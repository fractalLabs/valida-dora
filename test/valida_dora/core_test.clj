(ns valida-dora.core-test
  (:require [clojure.test :refer :all]
            [valida-dora.core :refer :all]))

(deftest single-validation
  (testing "Can I make a simple file validation?"
    (is (= (validate "project.clj")
           '({:meta "head -n 1", :data "(defproject valida-dora \"0.1.0-SNAPSHOT\"\n"}
             {:meta "file", :data "project.clj: ASCII text\n"}
             {:meta "wc -l", :data "       9 project.clj\n"})))))
