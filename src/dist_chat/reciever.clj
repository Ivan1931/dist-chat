(ns dist-chat.core)

(defn for-all
  "Checks if predicate is true for all elements in collection"
  [pred coll]
  (loop [iter coll]
    (if (empty? coll)
      true
      (if (pred (first iter))
        (recur (rest coll))
        false))))

(defn parse-date-or-now
  [date]
  (try (Date. date)
       (catch Exception d (Date. ))))

(def vec-conj (comp vec conj))
(def set-conj (comp set conj))

(defn add-message-to-contact
  "contacts is a contact hash of the form {:ip {:messages [messages] :online boolean :last-seen date :aliases [string] :meta {}}
  contact will be an ip address
  returns an updated contacts hash"
  [contacts contact {date :date message :message sender-alias :alias is-reply :reply}]
  (update-in contacts 
             [contact :messages] 
             (fn [messages] (vec-conj messages {:date date 
                                                :message message 
                                                :alias sender-alias 
                                                :reply is-reply}))))

(defn add-alias-to-contact
  "In the case that our contact is using a different alias to everyone else
  returns and updated contacts hash with an alias added to a new contact"
  [contacts contact sender-alias]
  (update-in contacts 
             [contact :aliases] 
             (fn [aliases] (set-conj aliases sender-alias))))

(defn handle-message
  "Updates shared contacts list based on the contents of our new message.
  Performs a synchronus update of contacts hash
  Handle will return a vector filled with either [:success] or [:error with the stack trace of the program]"
  [contact {date-text :date 
            message-text :message 
            sender-alias :alias}]
  (let [formatted-date (parse-date-or-now date-text)
        final-message {:date formatted-date
                       :message message-text
                       :alias sender-alias}]
    (dosync 
      (do
        (alter contacts 
               add-message-to-contact
               contact
               final-message)
        (alter contacts 
               add-alias-to-contact
               contact
               sender-alias)
        [:success]))))

(with-handler! (var handle-message)
  "Handler for now just deals all exceptions. Returns an error status with an exception"
  java.lang.Exception
  (fn [e & args] [:error e (.getStackTrace e)]))

(defn parse-json-or-nil
  "Tries to read json, if that fails return null"
  [string]
  (try 
    (json/read-json string)
    (catch Exception e nil)))

(defn inbox-dispatch
  "dispatch handles messages recieved on the inbox
  message is interpreted and then added to the contacts table if it is correct
  If all goes according to plan, we respond to the listening socket with a :success tag.
  If the sender does not recieve a success tag, it will assume that the message was not successfully sent"
  [socket]
  (let [raw-message-data (read-until-done socket)
        message (format-command raw-message-data)
        contact (->> socket .getInetAddress .toString strip-forward-slashes)
        message-data (json/read-json message)]
    (match (handle-message (log-info "Message recieved from " contact)
                           (log-info "Message-recieved" message-data))
           [:error message] (do (log-error "Error while recieving message"
                                           message)
                                (write-to socket (make-transmission :internal-error))
                                (.close socket))
           [:success] (do (log-info "Succesfully processed message, sending reply" :success)
                          (write-to socket (make-transmission :success))
                          (.close socket)))))

;;Error handlers for inbox server
(with-handler! (var inbox-dispatch)
  "Catch a possible json error"
  java.lang.Exception
  (fn [e socket] (do (log-exception "Possible json-parse error" e)
                     (write-to socket (make-transmission :json-parse-error))
                     (.close socket))))

(with-handler! (var inbox-dispatch)
  "Catch errors when reading from socket"
  java.io.IOException
  (fn [e socket] (do (log-exception "IO error when recieving message" e)
                     (.close socket))))

(with-handler! (var inbox-dispatch)
  "What to do when some of the required fields for a message are not transmitted. 
  This will set off a null pointer error"
  java.lang.NullPointerException
  (fn [e socket] (do (log-exception "Possibly incorrect fields in message" e)
                     (write-to socket (make-transmission :unrecognised-message-fields))
                     (.close socket))))

(defn inbox-timeout-handler
  "Handles timeout if inbox takes too long to occur"
  [socket]
  (do (log-error "Timeout while processing message send from socket" socket)
      (write-to socket 
                (make-transmission :timeout))))

(defn create-inbox-server
  "Creates a server that listens on port and handles all messages sent to this chat node"
  [port]
  (create-timed-server port
                       inbox-dispatch
                       inbox-timeout
                       inbox-timeout-handler))
