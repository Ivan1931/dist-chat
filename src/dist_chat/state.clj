(ns dist-chat.core)

;; Contacts will store a hash of the following form
;; {:ip {:messages [messages] :online boolean :last-seen date :aliases [string] :meta {}}

(def message-reciever-port 7070)
(def contacts (ref {}))
(def my-alias "Jonah")

(defn load-contacts
  [contacts-path])
