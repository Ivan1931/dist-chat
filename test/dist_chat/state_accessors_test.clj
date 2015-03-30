(ns dist-chat.core-test
  "Tests functions that will somehow modify the programs shared state.
  That is mostly the contacts and storage of messages"
  (:import [java.util Date]))

(deftest add-alias-to-contact-test
  "Tests adding a new known alias to a contact"
  (do 
    (let [host "localhost"
          message-text "Hello, my spiny little friend"
          sender-alias "Porcupine"
          message {:alias sender-alias :message message-text :date (Date.)}
          contacts-with-message (add-message-to-contact {} "localhost" message)
          _contacts (add-alias-to-contact contacts-with-message "localhost" sender-alias)]
      (do
        (is (= (get-in _contacts [host :messages 0 :message] message-text)))
        (is (= (get-in _contacts [host :messages 0 :aliase] sender-alias)))))
    (let [host "125.214.21.4"
          alias-1 "The king of kings"
          alias-2 "The most kingly king ever"
          message-text-1 "Lorem, Ipsum, Something, Somethingelse, Another thing, there is nothing
                         Lorem, Ipsum, Something, Somethingelse, Another thing, the isthing
                         Lorem, Ipsum, Something, Somethingelse, Another thing, the isthing
                         Lorem, Ipsum, Something, Somethingelse, Another thing, the isthing
                         Lorem, Ipsum, Something, Somethingelse, Another thing, the isthing"
          message-text-2 "You incredible basterd. How I love you so much"
          sender-alias "Emperor"
          first-message {:message message-text-1 :alias sender-alias :date (Date.)}
          second-message {:message message-text-2 :alias sender-alias :date (Date.)}
          _contacts (-> {}
                        (add-message-to-contact host first-message)
                        (add-alias-to-contact host alias-1)
                        (add-message-to-contact host second-message)
                        (add-alias-to-contact host alias-2))]
      (do 
        (is (= (get-in _contacts [host :messages 1 :message] message-text-2)))
        (is (= (get-in _contacts [host :messages 0 :message] message-text-1)))
        (is (= (get-in _contacts [host :aliases]) #{alias-1 alias-2}))))))
