(ns dist-chat.core-test
  (:import (java.net Socket)))

;; Controller server can ;; -> Have resourses requested from it ;; -> Be asked to do things ;; We shall test both things seperately
(defn setup-resource-request-command-test
  "Sets up a test for a controller command that requests a resource from the controller and then sends that resource back to some other remote host.
  controller-port is the port number the controller server established by this method shall listen to.
  resource-reciever-port is the port number of socket to which the command will interact with.
  resource-reciever-dispatch is a dispatch that handles the socket created during the communication.
  reciever-promise a promise in which the results of sending data to the reciever dispatch will be sent.
  command is a string that will be sent to the command server.
  timeout is the ammount of time the command must ideally be performed."
  [controller-port 
   resource-reciever-port 
   resource-reciever-dispatch
   command
   reciever-promise
   timeout]
  (let [controller-server (future (create-controller-server controller-port))
        reciever-server (future (create-server resource-reciever-port 
                                               (partial resource-reciever-dispatch
                                                        reciever-promise)))
        socket (Socket. "localhost" controller-port)]
    (do (write-to socket command)
        (Thread/sleep timeout)
        (do-repeatedly future-cancel controller-server reciever-server)
        (.close socket)
        (deliver reciever-promise :timeout))))

(defn listen-for-message-delivery
  [message-promise socket]
  (let [lines (read-until-done socket)] 
    (do (deliver message-promise (json/read-json (first lines)))
        (.close socket))))

(deftest dispatch-message-send-test
  "Tests the send-message command
  Sends a correct message and listens for it on another server"
  (let [controller-port 10001
        reciever-port 10002
        message-promise (promise)
        test-message "Hello"
        command-message (str (json/write-str {:send-message 
                                              {:port reciever-port 
                                               :host "localhost" 
                                               :message test-message}}) 
                             "\n" :done)]
    (do (setup-resource-request-command-test controller-port
                                             reciever-port
                                             listen-for-message-delivery
                                             command-message
                                             message-promise
                                             1500)
        (is (not= @message-promise :timeout))
        (is (= (@message-promise :message) 
               test-message)))))

(deftest check-online-test
  "Tests whether the check online command works for both online and offline users"
  (let [controller-port 7000
        echo-port 8000
        fail-port 8001
        controller-server (future (create-controller-server controller-port))
        echo-server (future (create-server echo-port echo-dispatch))
        alive-server-command {:check-online {:host "localhost" :port echo-port}}
        dead-server-command {:check-online {:host "localhost" :port fail-port}}
        online-socket (create-socket "localhost" controller-port)
        dead-socket (create-socket "localhost" controller-port)]
    (do (write-to online-socket 
                  (make-transmission alive-server-command))
        (let [response (read-until-done online-socket)]
          (is (= (parse-server-response response) 
                 {:user "localhost" :online true})))
        (write-to dead-socket
                  (make-transmission dead-server-command))
        (let [response (read-until-done dead-socket)]
          (is (= (parse-server-response response)
                 {:user "localhost" :online false})))
        (future-cancel echo-server)
        (future-cancel controller-server))))
                  
