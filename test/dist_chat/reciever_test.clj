(ns dist-chat.core-test)

(defn setup-inbox-test
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
  (let [controller-port 7991
        inbox-port 1997
        test-message "hello"]
    (do (setup-inbox-test inbox-port controller-port test-message)
        (println "Contacts " @contacts)
        (is (= (get-in @contacts 
                       ["/127.0.0.1" :messages 0 :alias]) 
               my-alias ))
        (is (= (get-in @contacts 
                       ["/127.0.0.1" :messages 0 :message])
               test-message)))))

