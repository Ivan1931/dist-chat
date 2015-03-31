(ns dist-chat.core)

;; This is an impure function, should probably figure out how to fix that
(defn send-message 
  "Sends a message to the host specified in the message data"
  [address-information]
  (match [address-information]
         [{:host host :port port :message message-text}]
         (let [message (make-transmission {:date (.toString (Date.)) 
                                           :message message-text
                                           :alias my-alias})
               socket (create-socket host port)]
           (do (log-message (str "Sending to " socket)
                            message)
               (write-to socket message)
               (let [response (timed-worker message-timeout
                                            (read-until-done socket))]
                 (if (= response :timeout)
                   :acknowledgement-failure
                   :success))))
         [{:ip address :message message-text}]
         (send-message {:host address :port message-reciever-port :message message-text})))

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
  "Performs a server command
  Currently sends messages and requests contacts"
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
  (loop [command-lines (read-until-done socket)
         command-string (format-command command-lines)]
    (do (perform-command (log-message "Socket value" socket) 
                         (log-message "Command String" command-string))
        (.close socket))))

(defn create-controller-server
  "Creates a controller server listening on specified port"
  [port]
  (create-server port controller-dispatch))
