(ns dist-chat.core
  (:require [clojure.java.io :refer [writer reader]])
  (:import (java.io BufferedReader BufferedWriter)
           (java.net Socket ServerSocket)))

(defn create-socket
  [host port]
  "Creates a client socket that connects to the specified host and server number"
  (Socket. host port))

(defn create-server-socket
  [port]
  "Creates a server socket at the specified port"
  (ServerSocket. port))

(defn create-dispatch
  [socket f]
  "Calls ServerSocket.accept and creates a new future which performs statefull io using f"
  (->> socket f future))

(defn create-server
  [port dispatch]
  "Creates a parrelelised server listening to a port.
  Server will call server socket accept and spawn a new thread with the resulting socket
  which handles the socket using the dispatch function"
  (let [server (create-server-socket port)]
    (while true
      (let [socket (.accept server)]
        (create-dispatch socket dispatch)))))

(defn echo-dispatch
  [socket]
  (let [d (rand)
        out (BufferedWriter. (writer socket))
        in  (BufferedReader. (InputStreamReader. (.getInputStream socket)))]
    (loop [line (.readLine in)]
      (if (= line "CLOSE")
        (do 
          (println "Client closed connection")
          (.close socket))
        (do 
          (.write out (str "Echo " d " " line "\n"))
          (.flush out)
          (println d " echoed " line)
          (recur (.readLine in)))))))

(defn echo-server
  [port]
  (create-server port echo-dispatch))
