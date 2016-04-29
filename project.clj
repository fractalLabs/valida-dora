(defproject dora "1.0.0-SNAPSHOT"
  :description "Dora la exploradora de datos"
  :dependencies [[clj-http "2.0.0"]
                 [clj-pdf "2.2.0"]
                 [clj-time "0.11.0"]
                 [clj-zendesk "0.1.0"]
                 [clojail "1.0.6"]
                 [cloogle "0.1.0-SNAPSHOT"]
                 [com.cemerick/friend "0.2.1"]
                 [com.draines/postal "1.11.3"]
                 [commons-lang/commons-lang "2.6"]
                 [com.novemberain/monger "3.0.1"]
                 [compojure "1.4.0"]
                 [environ "1.0.2"]
                 [http-kit "2.1.18"]
                 [lib-noir "0.8.5"];"0.9.9"
                 [log4j/log4j "1.2.17"]
                 ;[medley "0.7.0"]
                 [mongerr "1.0.0-SNAPSHOT"]
                 [nillib "0.1.0-SNAPSHOT"]
                 [nlp "0.1.0-SNAPSHOT"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/core.cache "0.6.4"]
                 [org.clojure/data.json "0.2.6"]
                 [org.clojure/tools.logging "0.3.1"]
                 [ring/ring-json "0.4.0"]
                 [ring-cors "0.1.7"];"0.1.7"
                 [ring-server "0.4.0"]
                 [com.rpl/specter "0.9.3"]
                ]
  :jvm-opts ["-Djava.security.policy=.java.policy" "-Xmx16g"]
  :plugins [[lein-ring "0.9.7"]]
  :repl-options {:init-ns dora.repl
                 :timeout 180000}
  :ring {:handler dora.server/app :port 5555})
