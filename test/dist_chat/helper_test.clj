(ns dist-chat.core-test)

(deftest test-if-server-listening
  (let [live-port 5555
        dead-port 5556
        server (future (create-server live-port echo-dispatch))]
    (do (is (server-listening? "localhost" live-port))
        (is (not (server-listening? "localhost" dead-port)))
        (future-cancel server))))

(defn dummy-dispatch
  [timeout socket]
  (do (Thread/sleep timeout)
      (write-to socket 
                (make-transmission :done))
      (.close socket)))

(defn dummy-timeout-handler
  [socket]
  (do (write-to socket 
                (make-transmission :timeout))))

(deftest server-timeout-fail-test
  (let [port 4000
        timeout 500
        server (future (create-timed-server port
                                                 (partial dummy-dispatch 
                                                          (* timeout 2))
                                                 timeout
                                                 dummy-timeout-handler))
        socket (create-socket "localhost" port)]
    (do (write-to socket "")
        (let [response (->> socket read-until-done parse-server-response)]
          (is (= response "timeout")))
        (.close socket)
        (future-cancel server))))

(deftest server-timeout-success-test
  (let [port 4001
        timeout 500
        server (future (create-timed-server port
                                                 (partial dummy-dispatch 
                                                          (/ timeout 4))
                                                 timeout
                                                 dummy-timeout-handler))
        socket (create-socket "localhost" port)]
    (do (write-to socket "")
        (let [response (->> socket read-until-done parse-server-response)]
          (is (= response "done")))
        (.close socket)
        (future-cancel server))))
