(ns dist-chat.core-test
  (:require [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.test :refer :all]
            [dist-chat.core :refer :all]))

(def msg (promise))
(def controller-port 8888)
(def reciever-port 8889)
(def test-message "Hello")
(def expected-msg (str "{message:\"" test-message "\"}\n:done"))
(def send-msg-test-string (str (json/write-str {:send-message {:port reciever-port :host "localhost" :message test-message}}) "\n:done"))

(defmacro dbg
  [debug-message & form]
  `(do (println ~debug-message)
      ~form))

(defn fill-msg-dispatch
  [socket]
  (let [lines (string/join "\n" (read-lines socket 2))]
    (do (deliver msg lines)
        (.close socket))))

(defn setup-command-dispatch-test
  [controller-port reciever-port]
  (do (future (create-controller-server controller-port))
      (future (create-server reciever-port fill-msg-dispatch))
      (Thread/sleep 100)))

(setup-command-dispatch-test controller-port reciever-port)

(let [socket (create-socket "localhost" controller-port)]
  (do (write-to socket send-msg-test-string)
      (.close socket)))

(deftest dispatch-message-send-test
  (is (= @msg expected-msg)))

(run-tests 'dist-chat.core-test)
