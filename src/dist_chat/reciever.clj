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
  [contacts contact {date :date message :message sender-alias :alias}]
  (update-in contacts 
             [contact :messages] 
             (fn [messages] (vec-conj messages {:date date :message message :alias sender-alias}))))

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
    (dosync (try 
              (do
                (alter contacts 
                       add-message-to-contact
                       contact
                       final-message)
                (alter contacts 
                       add-alias-to-contact
                       contact
                       sender-alias)
                [:success])
              (catch Exception e [:error (.getStackTrace e)])))))

(defn inbox-dispatch
  "dispatch handles messages recieved on the inbox
  message is interpreted and then added to the contacts table if it is correct"
  [socket]
  (let [raw-message-data (read-until-done socket)
        message (make-command raw-message-data)
        contact (->> socket .getInetAddress .toString)
        message-data (json/read-json message)]
    (match (handle-message (log-message "Message recieved from " contact)
                           (log-message "Message-recieved" message-data))
           [:error message] (do (log-message "Error while recieving message"
                                             message)
                                (write-to socket ((comp message-encase json/write-str) [:internal-error])))
           [:succes] (.close socket))))


(defn create-inbox-server
  "Creates a server that listens on port and handles all messages sent to this chat node"
  [port]
  (create-server port inbox-dispatch))
