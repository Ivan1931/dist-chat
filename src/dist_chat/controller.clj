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
           (do (log-info (str "Sending to " socket)
                         message)
               (write-to socket message)
               (let [response (timed-worker message-timeout
                                            (read-until-done socket))]
                 (if (not= response
                           [(quoterise "success")])
                   (log-error "Some thing went wrong when sending message" :command-failure)
                   (dosync 
                     (try (alter contacts add-message-to-contact
                                           host
                                           {:date (Date.)
                                            :alias my-alias
                                            :message message-text
                                            :reply true})
                                (catch Exception e (log-exception "Error when writing the message we sent to contacts" e)))
                           (log-info "Succesfully sent and processed message" :command-success)
                           :command-success)))))
         [{:ip address :message message-text}]
         (send-message {:host address :port inbox-port :message message-text})))

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

(defn perform-online-check
  [data]
  (match [data]
         [{:host host :port port}] {:user host 
                                    :online (server-listening? host port)}
         [{:host host}] {:user host
                         :online (server-listening? host inbox-port)}
         [_] (do (log-error "Invalid arguments for perform online check" data)
                 :error-invalid-request)))

(defn perform-command
  "Performs a server command
  Currently sends messages and requests contacts"
  [socket commad-string]
  (let [command (json/read-json commad-string)]
    (match [command]
           [{:send-message data}] 
           (let [result (send-message data)]
             (do (write-to socket 
                           (make-transmission result))))
           [{:request-contacts data}] 
           (let [contact-data (perform-contact-request data)]
             (write-to socket contact-data))
           [{:check-online data}]
           (let [user-status (perform-online-check data)]
             (write-to socket 
                       (make-transmission user-status))))))

(with-handler! (var perform-command)
  "If we have an unregonised command sent to the server, log it"
  java.lang.NullPointerException
  (fn [e & args]
    (log-exception "Unsupported command" e)))

(defn controller-dispatch
  "Dispatch handles commands recieved at the command listening port.
  Theoretically, the application can only send and recieve requests locally for now. 
  Currently recognised commands are send-message and request-contact.
  All calls on this dispatch are blocking calls.  "
  [socket]
  (loop [command-lines (read-until-done socket)
         command-string (format-command command-lines)]
    (do (perform-command (log-info "Socket value" socket) 
                         (log-info "Command String" command-string))
        (.close socket))))

(defn controller-timeout-handler
  [socket]
  (do (log-error "Timeout while processing socket" socket)
      (write-to socket 
                (make-transmission :timeout))))

(defn create-controller-server
  "Creates a controller server listening on specified port"
  [port]
  (create-timed-server port 
                       controller-dispatch
                       controller-timeout
                       controller-timeout-handler))
