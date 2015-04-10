(ns dist-chat.core
  (:require [cheshire.core :as ches :refer :all]))

;; Contacts will store a hash of the following form
;; {:ip {:messages [messages] :online boolean :last-seen date :aliases [string] :meta {}}

(def inbox-port 13616)
(def contacts-path ".dist_chat_contents")
(def alias-path ".alias")
(def inbox-timeout 10000)
(def controller-timeout 10000)
(def message-timeout 5000)
(def logging true)
(def continous-save-path ".dist_chat_continous_save")

(defn- load-continous-save
  []
  (-> continous-save-path slurp ches/parse-string))

(def continuous-save (atom (try
                          (load-continous-save)
                          (catch Exception e true))))

(defn save-continous-save
  []
  (->> @continuous-save ches/encode (spit continous-save-path)))


(defn- stringify-symbol
  "Turns a symbol into string without : at the beginning"
  [sym]
  (->> sym str rest (apply str)))

(defn- stringify-ips
  "Deals with functions been interned when the load-contacts function is loaded. Major hack"
  [loaded-contacts]
  (reduce (fn [acc [key value]]
            (assoc acc 
                   (stringify-symbol key)
                   value)) 
          {}
          loaded-contacts))

(defn- load-contacts
  []
  (-> contacts-path 
      slurp 
      (ches/decode true
                   (fn [field-name]
                     (if (= field-name "aliases") #{} [])))
      stringify-ips))

(defn- load-alias
  []
  (-> alias-path slurp))

(def my-alias
  (atom (try (load-alias)
             (catch Exception e "self"))))


(def contacts (ref (try (load-contacts)
                        (catch Exception e {"127.0.0.1" {:aliases #{"self"}}}))))

(defn save-contacts
  "Saves contacts or a contacts hash"
  ([]
    (save-contacts @contacts))
  ([conts]
    (->> conts generate-string (spit contacts-path))))

(defn save-alias
  [new-alias]
  (spit alias-path
        new-alias))
