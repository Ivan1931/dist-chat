(ns dist-chat.core
  (:require [clojure.java.io :refer [writer reader]])
  (:import (java.io BufferedReader BufferedWriter)
           (java.net Socket ServerSocket)))

(defn create-socket
  "Creates a client socket that connects to the specified host and server number"
  [host port]
  (Socket. host port))

(defn create-server-socket
  "Creates a server socket at the specified port"
  [port]
  (ServerSocket. port))

(defn create-dispatch
  "Calls ServerSocket.accept and creates a new future which performs statefull io using f"
  [socket f]
  (->> socket f future))

(defn create-server
  "Creates a parrelelised server listening to a port.
  Server will call server socket accept and spawn a new thread with the resulting socket
  which handles the socket using the dispatch function"
  [port dispatch]
  (let [server (create-server-socket port)]
    (while true
      (let [socket (.accept server)]
        (create-dispatch socket dispatch)))))

(defmacro do-repeatedly
  [f & args]
  `(do ~@(map (fn [o] `(~f ~o)) args)))

(defn write-to
  "Writes the specified string to the socket"
  [socket string]
  (let [out (BufferedWriter. (writer socket))]
    (do (.write out string)
        (.flush out)
        (.close out))))

(defn read-lines
  "Reads n lines from a socket. Blocks until n lines are provided"
  [socket n]
  (let [in (BufferedReader. (reader socket))]
    (loop [i n
           lines []]
      (if (> i 0)
        (recur (dec i) 
               (conj lines (.readLine in)))
        (do (.close socket)
            lines)))))

(defn read-until
  "Reads lines from socket until a line appears that satisfies the predicate.
  Once the reading is done, will return lines read as a predicate.
  Blocks until predicate is satisfied"
  [socket predicate]
  (let [in (BufferedReader. (reader socket))]
    (loop [lines []]
      (let [line (.readLine in)]
        (if (predicate line)
          (do (.close in)
              lines)
          (recur (conj lines line)))))))

(defn echo-dispatch
  [socket]
  (let [d (rand)
        out (BufferedWriter. (writer socket))
        in  (BufferedReader. (reader socket))]
    (loop [line (.readLine in)]
      (if (= line "CLOSE")
        (do 
          (println "Client closed connection")
          (do-repeatedly .close out in socket))
        (do 
          (.write out (str "Echo " d " " line "\n"))
          (.flush out))))))
