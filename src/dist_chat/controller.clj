(ns dist-chat.core)

;; This is an impure function, should probably figure out how to fix that
(defn send-message "Sends a message to the host specified in the message data"
  [address-information]
  (match [address-information]
         [{:host host :port port :message message}]
         (let [json-message (json/write-str {:date (.toString (Date. )) :message message :alias my-alias})
               string (str json-message "\n" :done)
               socket (create-socket host port)]
           (do (write-to socket string)
               (.close socket)))
         [{:ip address :message message}]
         (send-message {:host address :port message-reciever-port :message message})))

(defn find-values-for
  [h ks]
  "Finds all values in a hash (h) from a list of keys k"
  (reduce (fn [found k] (let [value (h k)] (if-not (nil? value) (conj found value) found))) [] ks))

(defn filter-map
  [hm pred]
  (mapv hash-map (filter (fn [[k v]] (pred k)) hm)))

(defn perform-contact-request
  "Takes a state hash"
  [[contacts aliases] request]
  (let [search-for-names (fn [names] {:some (filter-map contacts (partial contains? names))})]
    (match [request]
           [{:ips {:all _}}] contacts
           [{:aliases {:all _}}] contacts
           [{:ips {:some names}}] (search-for-names @contacts names)
           ;; Contacts is structure {:ip {:aliases #{aliases} :messages [{:message message} {:date date} {:sender-alias alias}]}}
           ;;[{:aliases {:some names}}] ()
           [_] {:error "Bad request"})))

(defn perform-command
  [socket commad-string]
  (let [command (json/read-json commad-string)]
    (match [command]
           [{:send-message data}] 
           (send-message data)
           [{:request-contacts data}] 
           (let [contact-data (perform-contact-request data)]
             (write-to socket contact-data)))))

(defn controller-dispatch
  "Dispatch handles commands recieved at the command listening port.
  Theoretically, the application can only send and recieve requests locally for now. 
  Currently recognised commands are send-message and request-contact.
  All calls on this dispatch are blocking calls.  "
  [socket]
  (loop [predicate (fn [line] (= ":done" line))
         command-lines (read-until socket predicate)
         command-string (make-command command-lines)]
    (perform-command (log-message "Socket value" socket) 
                     (log-message "Command String" command-string))))

(defn create-controller-server
  "Creates a controller server listening on specified port"
  [port]
  (create-server port controller-dispatch))
