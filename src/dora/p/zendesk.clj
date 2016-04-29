(ns dora.p.zendesk
  (:use clj-zendesk.core
        nillib.formats
        nillib.text)
  (:require [org.httpkit.client :as client]
            [clojure.data.json :as json]
            [clj-pdf.core :refer :all]
            [clj-time.format :as f]
            [clj-time.core :as t]
            [environ.core :refer [env]]
            [mongerr.core :refer :all]))

(setup "mxabierto" (env :zendesk-email) (env :zendesk-password))

(def zen-auth {:basic-auth [(env :zendesk-email) (env :zendesk-password)]})

(defn handle-response [{:keys [status headers body error]}]
  (if error
    (println "Failed, exception is " error)
    (json/read-str body :key-fn keyword)))

(defn request
  [endpoint]
    (client/get (str "https://mxabierto.zendesk.com/api/v2/" endpoint ".json")
                {:basic-auth [(env :zendesk-email) (env :zendesk-password)]}
                handle-response))

(defn create-user
  ([name email] (create-user name email {}))
  ([name email map-to-merge]
    (client/post (str "https://mxabierto.zendesk.com/api/v2/users.json")
                 {:basic-auth [(env :zendesk-email) (env :zendesk-password)]
                  :headers {"Content-Type" "application/json"}
                  :body (json/write-str {:user (merge {:name name :email email :verified true}
                                                      map-to-merge)})}
      handle-response)))

(defn update-user
  [user-id new-data]
    (client/put (str "https://mxabierto.zendesk.com/api/v2/users/" user-id ".json")
                {:basic-auth [(env :zendesk-email) (env :zendesk-password)]
                 :headers {"Content-Type" "application/json"}
                 :body (json/write-str {:user new-data})}
      handle-response))

(defn ticket
  ([conf]
   (create Ticket conf))
  ([email subject body]
   (ticket {:requester {:email email}
            :subject subject
            :comment {:body body}})))

(defn ticket-downtime [body]
  (ticket {:subject "Sitios caidos"
           :tags ["alerta-automatica downtime"]
           :comment {:body body}}))

(defn read-tickets [data]
  (json/read-str (:body data) :key-fn keyword))


(defn tickets
  ([] (tickets "https://mxabierto.zendesk.com/api/v2/tickets.json"))
  ([url]
    (client/get url
                {:basic-auth [(env :zendesk-email) (env :zendesk-password)]}
                handle-response)))

(defn all-tickets []
  (loop [t (tickets)
         all []]
    (if (:next_page @t)
      (recur (tickets (:next_page @t))
             (concat all (:tickets @t)))
      (concat all (:tickets @t)))))

(defn due-tickets [ticks]
  (filter #(or (= "open" (:status %))
               (= "pending" (:status %))) ticks))

(defn done-tickets [ticks]
  (filter #(or (= "closed" (:status %))
               (= "solved" (:status %))) ticks))

(def important-keys [:subject :description :tags :created_at :status :assignee_id  :id])

(defn tickets-inform []
  (let [t (due-tickets (all-tickets))
        data (map #(select-keys % important-keys) t)]
    (csv "Informe-Tickets.csv" data)))

(defn closed-tickets-inform []
  (let [t (done-tickets (all-tickets))
        ;data (map #(select-keys % important-keys) t)
        ]
    (csv "Informe-Tickets-Resueltos.csv" t)))


(defn gram-1 [tickets]
  (estudio-de-keywords (tickets-text tickets)))

(defn gram-2 []
  (ngram-study 2 (tickets-text (all-tickets))))

(defn attachments
  ([url]
    (client/get "https://mxabierto.zendesk.com/api/v2/attachments/401.json"
                {:basic-auth [(env :zendesk-email) (env :zendesk-password)]}
                (fn [{:keys [status headers body error]}]
                  (if error
                    (println "Failed, exception is " error)
                    (json/read-str body :key-fn keyword))))))

(defn organizations
  ([] (organizations "https://mxabierto.zendesk.com/api/v2/organizations.json"))
  ([url]
    (client/get url
                {:basic-auth [(env :zendesk-email) (env :zendesk-password)]}
                (fn [{:keys [status headers body error]}]
                  (if error
                    (println "Failed, exception is " error)
                    (json/read-str body :key-fn keyword))))))

(defn all-organizations []
  (loop [t (organizations)
         all []]
    (if (:next_page @t)
      (recur (organizations (:next_page @t))
             (concat all (:organizations @t)))
      (concat all (:organizations @t)))))

(defn users
  ([] (users "https://mxabierto.zendesk.com/api/v2/users.json"))
  ([url]
    (client/get url
                {:basic-auth [(env :zendesk-email) (env :zendesk-password)]}
                (fn [{:keys [status headers body error]}]
                  (if error
                    (println "Failed, exception is " error)
                    (json/read-str body :key-fn keyword))))))

(defn all-users []
  (loop [t (users)
         all []]
    (if (:next_page @t)
      (recur (users (:next_page @t))
             (concat all (:users @t)))
      (concat all (:users @t)))))

(defn end-users []
  (filter #(= "end-user" (:role %)) (all-users)))

(defn user-id [email]
  (:id (first (filter #(= email (:email %)) (all-users)))))

(def usrs (all-users))
(defn user-data [id]
  (select-keys (first (filter #(= id (:id %)) usrs)) [:name :email]))

(defn satisfaction
  ([] (satisfaction "https://mxabierto.zendesk.com/api/v2/satisfaction_ratings.json"))
  ([url]
   (client/get url
               zen-auth
               (fn [{:keys [status headers body error]}]
                 (if error
                   (println "Failed, exception is " error)
                   (json/read-str body :key-fn keyword))))))

(defn all-satisfaction []
  (loop [t (satisfaction)
         all []]
    (if (:next_page @t)
      (recur (satisfaction (:next_page @t))
             (concat all (:satisfaction_ratings @t)))
      (remove #(= "offered" (:score %)) (concat all (:satisfaction_ratings @t))))))

;; Reportes
(defn reporte-infotec [email month day]
  (let [id (user-id email)
        data (filter #(= id (:assignee_id %)) (done-tickets (all-tickets)))
        data (filter #(t/before? (t/date-time (t/year (t/now)) month day)
                                 (f/parse (:updated_at %)))
                     data)]
    data))

(defn format-reporte [data]
  (let [data (map #(assoc %1 :Req %2 :Resultado "Entregado" :Estado "T" :Avance "100%")
                  data
                  (map inc (range)))]
    (csv "Reporte.csv" data [:Req :type :subject :Resultado :Estado :Avance])))


(defn pdf-evidencia [data]
  (pdf [{}
        [:paragraph (str
"Comentarios que Sugieren Atención a un Recurso de Datos


Estimado Administrador de Datos,

En un intento por mejorar el servicio de Datos Abiertos, perfeccionar los Recursos de Datos que las

Dependencias de la Administración Pública publican y asegurar su accesibilidad y permanencia, esta

Dirección General ha realizado un ejercicio de prueba ­con la intención de hacerlo permanente­ para

comprobar el funcionamiento de la descarga de sus recursos de datos. Durante dicha prueba,

detectamos posibles problemas con los siguientes recursos de datos bajo su responsabilidad:

URL: " (:url data) "

Fecha de prueba: " (:now data)"

Los errores pueden ser los siguientes:

1.­ El servidor no está disponible.

2.­ El recurso requiere derechos de acceso.

3.­ El servidor toma demasiado tiempo en responder a una solicitud por el recurso.

Amablemente sugerimos atender dichas ligas y revisar todos los conjuntos de datos restantes que su

dependencia publica en el sitio. Sin más por el momento, me mantengo a su disposición para resolver

cualquier duda sobre el proceso de cumplimiento de la Política de Datos Abiertos en el correo

escuadron@datos.gob.mx o vía telefónica al 50935300 ext: 7054.

Saludos cordiales.

Coordinación de Estrategia Digital Nacional

Presidencia de la República

www.datos.gob.mx")]]
       (str "pdfs/reporte-" (rand) ".pdf")))

(defn pdfs-month-ligas-rotas []
  (let [data (filter #(t/before? (t/date-time (t/year (t/now)) (t/month (t/now)) 1)
                                 (:now %))
                     (db-find :status-broken))]
    data))
