(ns dist-chat.core
  (:require [clojure.core.match :refer [match]]
            [clojure.data.json :as json]))

(defn send-message
  "Sends a message to the host specified in the message data"
  [{host :host port :port message :message}]
  (let [json-message (json/write-str {:message message})
        string (str json-message "\n" :done)
        socket (create-socket host port)]
    (do (write-to socket string)
        (.close socket))))

;;Dummy contact request until I set that up
(defn perform-contact-request
  [{contacts :all}]
  {"Jonah" "10.0.0.23"})

;;Dummy conversation request handler until I set that up
(defn perform-conversation-request
  [request]
  (match [request]
         [{:all []}] []
         [_] {:error "Bad request"}))

(defn perform-command
  [socket commad-string]
  (let [command (json/read-json commad-string)]
    (match [command]
           [{:send-message data}] 
           (future (send-message data))
           [{:request-contact data}] 
           (let [contact-data (perform-contact-request data)]
             (write-to socket contact-data))
           [{:request-conversation data}] 
           (let [conversation-data (perform-conversation-request data)]
             (write-to socket conversation-data)))))

(defn controller-dispatch
  [socket]
  (let [in (BufferedReader. (reader socket))]
    (loop [line (.readLine in)
           command-string line]
      (if (= line ":done")
        (do (perform-command socket command-string)
            (.close in)
            (.close socket)
            command-string)
        (recur (.readLine in) 
               (str command-string line))))))

(defn create-controller-server
  [port]
  (create-server port controller-dispatch))
