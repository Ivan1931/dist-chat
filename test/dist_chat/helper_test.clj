(ns dist-chat.core-test)

(deftest test-if-server-listening
  (let [live-port 5555
        dead-port 5556
        server (future (create-server live-port echo-dispatch))]
    (do (is (server-listening? "localhost" live-port))
        (is (not (server-listening? "localhost" dead-port)))
        (future-cancel server))))
