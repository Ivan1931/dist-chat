(ns dist-chat.core)

;; Contacts will store a hash of the following form
;; {:ip {:messages [messages] :online boolean :last-seen date :aliases [string] :meta {}}

(def inbox-port 13616)
(def inbox-timeout 10000)
(def controller-timeout 10000)
(def contacts (ref {"127.0.0.1" {:aliases #{"self"}}}))
(def my-alias "Jonah")
(def message-timeout 1000)
(def logging true)

(defn load-contacts
  [contacts-path])
