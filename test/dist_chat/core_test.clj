(ns dist-chat.core-test
  (:require [clojure.tools.trace :refer :all]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [dist-chat.core :refer :all]))

(def msg (promise))
(def controller-port 7888)
(def reciever-port 7889)
(def test-message "Hello")
(def expected-msg (str (json/write-str {:message test-message}) "\n" :done))
(def send-msg-test-string (str (json/write-str {:send-message {:port reciever-port :host "localhost" :message test-message}}) "\n" :done))


;; So what do we need to do?
;; Need to have a way to capture the output of a command
;; -> Use a promis. Promis is fulled by some function with the output of our command
;; Need to start a command server
;; -> Pretty straight forward
;; May want to setup some sort of mock state for the command to run 
;; -> The state will be stored in a bunch of in memory atoms. Perhaps we might want to assertain the value of that approach
;; -> Defintely cannot be thread local. So these will have to be globally available atoms. This will be the situation for now
;; -> Need a function to setup the state
;; Actually, all we need to do is to write to the promise where to input is expected
;; So we merely have to pass a function that writes to a promise
;; Perhaps we will also want to include a timeout facility so that we can fail the test instead of blocking the main thread

(defmacro before
  [message & form]
  `(do (println "Message is: " ~message)
       ~form))

(defn run-controller-test
  [controller-port setup prom timeout command-resolver & args]
  (do setup
      (let [controller-server (future (create-controller-server controller-port))
            command-worker (future (apply command-resolver args))]
        (do (Thread/sleep timeout)
            (do-repeatedly future-cancel controller-server command-worker)
            (if (realized? prom)
              prom
              (deliver prom :timeout))))))

(defn test-message-reciever-dispatch
  [socket]
  (let [lines (string/join "\n" (read-lines socket 2))]
    (do (deliver msg lines)
        (.close socket))))

(defn send-and-recieve-message
  [controller-port reciever-port message-command]
  (let [reciever-worker (future (create-server reciever-port 
                                               test-message-reciever-dispatch))
        socket (create-socket "localhost" controller-port)
        write-worker (write-to socket message-command)]
    (do (write-worker)
        (Thread/sleep 100)
        (.close socket)
        (do-repeatedly future-cancel reciever-worker write-worker))))

(run-controller-test controller-port nil msg 1000
                  send-and-recieve-message 
                  controller-port
                  reciever-port 
                  send-msg-test-string)

(deftest dispatch-message-send-test
  (is (= @msg expected-msg)))

(run-tests 'dist-chat.core-test)
