(ns dora.core
  (:use clojail.jvm)
  (:require [cemerick.friend :as friend]
            [clojail.core :refer [sandbox]]
            [clojail.testers :refer :all]
            [clojure.set :refer :all]
            [clojure.stacktrace :refer [print-cause-trace]]
            [clojure.string :refer [trim]]
            [clojure.tools.logging :refer [info warn]]
            [mongerr.core :refer [db-find db-text-search]]
            [nillib.tipo :refer :all]
            [noir.response :as resp]
            [noir.session :as session])
  (:import (java.io StringWriter)
           (java.util.concurrent TimeoutException)))

(defn eval-form
  "Eval a form in a sandbox"
  [form sbox]
  (with-open [out (StringWriter.)]
    (let [result (sbox form {#'*out* out})]
      {:expr form
       :result [out result]})))

(defn eval-string
  "Eval a string in a sandbox"
  [expr sbox]
  (let [form (binding [*read-eval* false] (read-string expr))]
    (eval-form form sbox)))

(defn current-auth
  "Usuario autenticado actualmente"
  []
  (:identity (friend/current-authentication)))

(defn my-fns []
  (:fns (first (db-find "users" {:username (:identity (friend/current-authentication))}))))

(def ^{:doc "A tester that attempts to be secure, and allows def and threads."}
  default-tester
  [(blacklist-objects [clojure.lang.Compiler clojure.lang.Ref clojure.lang.Reflector
                       clojure.lang.Namespace clojure.lang.Var clojure.lang.RT
                       java.io.ObjectInputStream])
   (blacklist-packages ["java.lang.reflect"
                        ;"java.security"
                        ;"java.util.concurrent"
                        "java.awt"])
   (blacklist-symbols
    '#{alter-var-root intern eval catch
       load-string load-reader addMethod  ns-resolve resolve find-var
       *read-eval* ns-publics ns-unmap set! ns-map ns-interns the-ns
       push-thread-bindings pop-thread-bindings future-call agent send
       send-off slurp pcalls pvals in-ns System/out System/in System/err
       with-redefs-fn Class/forName})
   (blacklist-nses '[clojure.main])
   (blanket "clojail")])

(defn make-sandbox
  "Create a sandbox"
  []
  (sandbox default-tester
           :context (-> (permissions (java.security.AllPermission.)) domain context)
           :jvm? true
           :timeout 180000
           :init '(do (require '[monger.operators :refer :all]
                               '[mongerr.core :refer [db db-find db-geo db-text-search]]
                               '[clojure.set :refer :all]
                               '[clojure.string :as s]
                               '[clojure.repl :refer [doc find-doc]])
                      (require '[dora.p.agente-web :refer [errors-today link-uptime-report sure-errors prepare-errors]]
                               '[dora.p.data-core :refer :all]
                               '[dora.pro-file :refer [validate]]);;PRESIDENCIA
                      (future (Thread/sleep 9000000)
                              (-> *ns* .getName remove-ns)))))

(defn eval-request
  "Eval a string, with timeout and exception handling"
  [expr session]
  (try
    (eval-string expr session)
    (catch TimeoutException _
      {:error 102 :message "Execution Timed Out!" :expr expr}) ;TODO? retry once
    (catch Exception e
      ;(print-cause-trace e)
      {:error 101 :message (str e) :expr expr}
      ;(println "expr: " expr ", count: " (count (db-text-search :resources expr))))))
      ;(eval-request (str "(db-text-search :resources \"" expr "\")") session)
      )))

(defn re-find?
  "Does the regex match the string?"
  [re s] (not (nil? (re-find re s))))

(defn string-enclosed?
  "Is char1 the first char of the string, and char2 the latest?"
  [char1 char2 s]
  (and (= char1 (first s))
       (= char2 (last s))))

(defn slist?
      "Does the string represent a list?"
      [s]
      (string-enclosed? \( \) s))

(defn svec?
      "Does the string represent a vector?"
      [s]
      (string-enclosed? \[ \] s))

(defn smap?
      "Does the string represent a hash map?"
      [s]
      (string-enclosed? \{ \} s))

(defn unenclosed-struc?
      "Is the string not enclosed by a list, vector or hashmap representation?"
      [expr]
      (not (or (slist? expr)
               (svec? expr)
               (smap? expr)
               (not (re-find? #" " expr)))))

(defn preproc
  "String preprocessing to be evaled.
   If the string-from is not enclosed by a data structure, it is enclosed in a vector"
  [expr]
  (if (unenclosed-struc? expr) (str "[" expr "]")
      expr))

(defn postproc
  "Post processing. The output is ensured to be a vector of maps"
  [o tipo]
  (case (tipo :tipo)
    :map [o]
    :coll (case (tipo :subtipo)
            :map o
            :coll (map #(zipmap (range) %) o)
            (map #(hash-map :data %) o))
    [{:data o}])) ;distinct?

(defn output-keys
  "Choose what keys will be output in the grid,
  and format them so ui-grid can use them"
  [output]
  (let [all-keys (sort (distinct (flatten (map keys output))))]
    (map #(hash-map :name %) all-keys)))

(defn sb
  "Find sandbox in session, or create one for repl use"
  []
  (if-let [s (session/get "sb")]
    (if (= (current-auth) (session/get "user"))
      s
      (do
        (session/put! "sb" (make-sandbox))
        (session/put! "user" (current-auth))
        (session/get "sb")))
    (do
      (session/put! "sb" (make-sandbox))
      (session/put! "user" (current-auth))
      (session/get "sb"))))

(defn eval-wrapper
  "Eval a string"
  [expr]
  (let [{:keys [expr result error message] :as res} (eval-request (preproc expr) (make-sandbox))]
    (if error
      res
      (let [[out res] result
            tipo (tipo-y-subtipo res)
            post (postproc res tipo)]
        {:expr (pr-str expr)
         :out (str out)
         :result post
         :error (str error)
         :collection (if-let [coll (re-find #"\*\*Collection: .*\n" (str out))]
                       (trim (re-find #" .*$" coll)))}))))

(defn replace-nil [expr replacement]
  (if (nil? expr)
      replacement
      expr))

(defn repl-route
  "Routing and jsonifying output"
  [args]
  (let [expr (if-let [prefn (args :prefn)]
               (str "(" prefn (args :expr) ")")
               (replace-nil (args :expr) " "))
        return (eval-wrapper expr)
        jsonp (args :jsonp)]
    (spit "/var/log/repl-route.log" (str args "\n") :append true)
    (try
      (if (empty? (return :error))
        (info args)
        (warn (merge args return)))
      (catch Exception e (warn "caught exception: " (.getMessage e))))
    (if jsonp
      (resp/jsonp jsonp return)
      (resp/json return))))
