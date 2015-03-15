(ns dist-chat.core
  (:require [clojure.core.match :refer [match]]
            [clojure.data.json :as json :refer [read-json write-str read-str]]
            [clojure.string :as string refer [join]]
            [clojure.tools.namespace.repl :refer [refresh]])
  (:import java.util.Date))

;; Contacts will store a hash of the following form
;; {:ip {:messages [messages] :online boolean :last-seen :aliases [string] :meta {}}
;; Aliases will be a hash like so
;; {:alias :ip}

(defmacro before
  [message & form]
  `(do (println (quote ~message) " ->\n" ~message "\n")
       ~form))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defmacro do-repeatedly
  [f & args]
  `(do ~@(map (fn [o] `(~f ~o)) args)))

(defmacro log-message
  [message body]
  `(let [x# ~body]
     (do (println "[LOG]" (.toString (Date. )) " ==>> " ~message " "))
         (println x#)
         x#))

(def message-reciever-port 7070)
(def contacts (ref {}))
(def my-alias "Jonah")

(defn load-contacts
  [contacts-path])

(defn load-aliases
  [alias-path])

(load "helper")
(load "reciever")
(load "sender")
(load "controller")
