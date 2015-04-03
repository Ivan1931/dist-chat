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

(defn create-timed-dispatch
  "Creates a dispatch that will create a worker to handle a socket with dispatch
  and allow it to run for timeout milliseconds. If the dispatch times out, the handler function will pass
  onto the timeout handler with will do something with the socket. 
  Also will cancell the worker and close the socket if timeout occurs"
  [socket dispatch timeout timeout-handler]
  (let [worker (->> socket dispatch (timed-worker timeout))]
    (when (= worker :timeout)
      (println "Timeout")
      (timeout-handler socket)
      (.close socket))))

(with-handler! (var create-timed-dispatch)
  "Log and handle any errors that may occur during a timed dispatch"
  java.lang.Exception
  (fn [e & args] (log-exception "Error whilst performing timed dispatch" e)))

(defn create-timed-server
  "Creates a dispatch server which will accept and interact with a socket connection for a specified ammount of time
  before passing the socket created to another function that handles timeouts.
  port is the port the server will listen too.
  dispatch is the function used to handle sockets created using (.accept server).
  timeout is the time in milliseconds allotted to a command for it to succeed.
  timeout-handler is a function that takes a socket as an argument and is called if the dispatch is timed out."
  [port dispatch timeout timeout-handler]
  (let [server (create-server-socket port)]
    (while true
      (let [socket (.accept server)]
        (future 
          (create-timed-dispatch socket 
                                 dispatch 
                                 timeout 
                                 timeout-handler))))))

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

(defn server-listening?
  "Tests if a server is listening at specified host and port by creating a socket"
  [host port]
  (try (let [socket (create-socket host port)]
         (do (.close socket)
             true))
       (catch java.net.ConnectException e false)))

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

(defn parse-server-response
  [command-lines]
  (json/read-json (format-command command-lines)))
