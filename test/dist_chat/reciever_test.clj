(ns dist-chat.core-test)

(defn setup-send-message-test
  [inbox-port controller-port test-message]
  (let [controller-server (future (create-controller-server controller-port))
        inbox-server (future (create-inbox-server inbox-port))
        socket (create-socket "localhost" controller-port)
        command {:send-message {:port inbox-port :message test-message :host "localhost"}}
        message (message-encase (json/write-str command))]
    (do (write-to socket message)
        (Thread/sleep 1000)
        (.close socket)
        (future-cancel inbox-server)
        (future-cancel controller-server))))

(deftest inbox-reciept-updates-contacts-test
  "Tests whether sending a correct message from the controller using send-message command updates
  the contacts list"
  (let [controller-port 7991
        inbox-port 1997
        test-message "hello"]
    (do (setup-send-message-test inbox-port controller-port test-message)
        (is (= (get-in @contacts 
                       ["/127.0.0.1" :messages 0 :alias]) 
               @my-alias))
        (is (= (get-in @contacts 
                       ["/127.0.0.1" :messages 0 :message])
               test-message)))))

(deftest incorrectly-formatted-message-test
  "Tests sending an incorrectly formatted message"
  (let [inbox-port 4444
        ill-formatted-message (message-encase "this is completely incorrect json")
        inbox-server (future (create-inbox-server inbox-port))
        socket (create-socket "localhost" inbox-port)]
    (do (write-to socket ill-formatted-message)
        (let [response-raw (read-until-done socket)
              response (format-command response-raw)]
          (do (is (= response "\"json-parse-error\""))
              (.close socket)
              (future-cancel inbox-server))))))
