(ns dora.server
  (:use compojure.core)
  (:require [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [compojure.route :as route]
            monger.json
            [noir.util.middleware :as nm]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body wrap-json-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [ring.middleware.nested-params :refer [wrap-nested-params]]
            [ring.middleware.params :refer [wrap-params]]
            [dora.core :refer :all]
            [dora.pro-file :refer :all]
            [mongerr.core :refer :all]
            [nillib.formats :refer :all]))

(defn generate-csv-response [data & [status]]
  {:status (or status 200)
   :headers {"Content-Type" "application/CSV"}
   :body (pr-str data)})

(defn map-vals [f m]
  (zipmap (keys m) (map f (vals m))))

(defn numerable? [s]
  (= s (re-find #"\A-?\d+" s)))

(defn parse-strings [params]
  (map-vals #(cond
               (numerable? %) (Integer/parseInt %)
               (= "true" %) true
               (= "false" %) false
               :else %)
            params))

(def app-routes
  [(ANY "/" [:as {params :params}]
        (do (println "Request: " params)
            (repl-route params)))
   (ANY "/validate" [& params]
        (validate params))
   (GET "/db" [& params]
        (println (:collection params) (dissoc params :collection))
        (db-find (:collection params) (parse-strings (dissoc params :collection))))
   (GET "/db/:collection" [& params]
        (println "? o que " params)
        params)
   (ANY "/csv" [:as {params :params}]
        (-> params
            :expr
            eval-wrapper
            :result
            csv-str
            generate-csv-response
            ))
   (ANY "/data/:collection" [collection]
        (db-find collection))
   (ANY "/geo/:coll" [:as {params :params}]
        (do (db-insert :log (assoc params :tipo "geo-search"))
         (db-geo (:coll params) [(read-string (:longitude params)) (read-string (:latitude params))])))
   (ANY "/text/:coll" [:as {params :params}]
        (do (db-insert :log (assoc params :tipo "text-search"))
            (db-text-search (:coll params) (:q params))))
   (GET "/auth" req
        (friend/authenticated (str "You have succesfully authenticated as "
                                   (friend/current-authentication))))
   (route/not-found "<h1>404 Not Found</h1>")])

(defn cors [routes]
  (wrap-cors routes :access-control-allow-origin [#".*"]
             :access-control-allow-methods [:get :put :post :delete]))

                                        ; a dummy in-memory user "database"
(def db-users {"root" {:username "root"
                       :password (creds/hash-bcrypt "admin_password")
                       :roles #{::admin}}
               "jane" {:username "jane"
                       :password (creds/hash-bcrypt "user_password")
                       :roles #{::user}}})

(defn get-users
  "Get all users from DB"
  []
  (apply merge (map #(hash-map (:username %) %)
                    (map #(assoc %
                                 :roles
                                 (set (map (partial keyword "interfaz.handler")
                                           (:roles %))))
                         (db-users "users")))))

;; define custom wrapping middleware as noir's middleware/app-handler does
;; its own thing with routes + middleware
(defn credential-fn [creds-map]
  (creds/bcrypt-credential-fn db-users creds-map))

(defn authenticate [handler]
  (friend/authenticate
   handler
   {:workflows [(workflows/interactive-form
                 :credential-fn credential-fn
                 ;:login-failure-handler login-failure-handler
                 :workflows  [(workflows/interactive-form)]
                 )
                ]}))

(def app (nm/app-handler app-routes
                         :middleware [wrap-params
                                      wrap-keyword-params
                                      wrap-nested-params
                                      authenticate
                                      wrap-json-response
                                      wrap-json-body
                                      wrap-json-params
                                      cors
                                      ]
                         :formats [:json-kw :edn]))

;(defn -main [port]
;  (jetty/run-jetty app {:port (Long. port) :join? false}))
(defn run
  ([] (run 5555))
  ([port] (jetty/run-jetty app {:port port})))
