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
  (try 
    (->> socket f future) 
    (catch Exception e 
      (log-info "Exception in dispatch" 
                (.getMessage e)))))

(defn create-server
  "Creates a parrelelised server listening to a port.
  Server will call server socket accept and spawn a new thread with the resulting socket
  which handles the socket using the dispatch function"
  [port dispatch]
  (let [server (create-server-socket port)]
    (while true
      (let [socket (.accept server)]
        (create-dispatch socket dispatch)))))

(defn write-to
  "Writes the specified string to the socket"
  [socket string]
  (let [out (BufferedWriter. (writer socket))]
    (do 
      ;;This is a fix because for some reason buffered writer was not writing entire lines during writes
      (doseq [lines (string/split string #"\n|\r\n")]
        (do (.write out string)
            (.newLine out))
      (.flush out)))))

(defn read-lines
  "Reads n lines from a socket. Blocks until n lines are provided"
  [socket n]
  (let [in (BufferedReader. (reader socket))]
    (loop [i n
           lines []]
      (if (> i 0)
        (recur (dec i) 
               (conj lines (.readLine in)))
        lines))))

(defn read-until
  "Reads lines from socket until a line appears that satisfies the predicate.
  Once the reading is done, will return lines read as a predicate.
  Blocks until predicate is satisfied"
  [socket predicate]
  (let [in (BufferedReader. (reader socket))]
    (loop [lines []]
      (let [line (.readLine in)]
        (if (predicate line)
          lines
          (recur (conj lines line)))))))

(defn read-until-done
  "Reads lines from socket until a line with only \":done\" appears on it"
  [socket]
  (let [predicate (fn [line] (= line ":done"))]
    (read-until socket predicate)))

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

(defn message-encase
  [message]
  (str message 
       \newline
       :done))

(defn format-command
  "Combines commands together using \n. Removes any line that say :done"
  [command-lines]
  (string/join "\n" 
               (filter (fn [line] (not= ":done" line)) 
                       command-lines)))

(defn make-transmission
  "Takes a hash and converts it into a sendable command"
  [message]
  (message-encase (json/write-str message)))
