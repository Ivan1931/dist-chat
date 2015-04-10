(ns dist-chat.core
  (:require [dire.core :refer [with-handler!]]
            [clojure.core.match :refer [match]]
            [clojure.data.json :as json :refer [read-json write-str read-str]]
            [clojure.string :as string refer [join]]
            [clojure.tools.namespace.repl :refer [refresh]])
  (:import java.util.Date))

(load "state")
(load "macros")
(load "helper")
(load "reciever")
(load "sender")
(load "controller")
(load "ui")

(defn -main
  [& args]
  (do (future 
        (create-inbox-server inbox-port))))
