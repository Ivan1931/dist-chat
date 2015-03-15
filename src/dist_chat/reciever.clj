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

(defn add-message-to-contact
  [{date :date message :message sender-alias :alias} contact]
  (assoc contact :messages (fn [messages] (vec-conj {:date date :message message :alias sender-alias}))))

(defn add-alias-to-contact
  [sender-alias contact]
  (assoc contact :alias (fn [aliases] (conj aliases sender-alias))))

(defn update-contact
  [contact message]
  (match [message]
         [{:date date :message message :alias sender-alias}]
         (let [formatted-date (parse-date-or-now date)
               update-messages (fn [contacts] 
                                 (update-in contact 
                                            (partial add-message-to-contact 
                                                     {:date formatted-date 
                                                      :message message 
                                                      :alias sender-alias})))
               update-aliases (fn [contacts] 
                                (update-in contact 
                                           (partial add-alias-to-contact 
                                                    sender-alias)))]
           (dosync (alter contacts update-messages)
                   (alter contacts update-aliases)))))

(defn message-reciever-dispatch
  [socket]
  (let [stop-predicate (fn [line] (= line ":done"))
        message (read-until socket stop-predicate)
        message-data (json/read-str message)
        contact (->> socket .getInetAddress .toString)]
    (update-contact contact message-data)))

(defn create-message-listener
  [port]
  (create-server port message-reciever-dispatch))
