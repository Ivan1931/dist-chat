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
  [contacts contact {date :date message :message sender-alias :alias}]
  (update-in contacts [contact :messages] (fn [messages] (vec-conj {:date date :message message :alias sender-alias}))))

(defn add-alias-to-contact
  [contacts contact sender-alias]
  (update-in contacts [contact :aliases] (fn [aliases] (set-conj aliases sender-alias))))

(defn update-contact
  [contact {date :date message :message sender-alias :alias}]
  (let [formatted-date (parse-date-or-now date)
        final-message {:date formatted-date
                       :message message
                       :alias sender-alias}]
    (dosync (println "Our data " date " " message " " sender-alias)
            (println "Contacts " contacts " Derefed" @contacts)
            (try 
              (if (contains? @contacts contact)
                (alter add-message-to-contact
                       contact
                       final-message)
                (alter contacts 
                       conj
                       {contact {:message [final-message] 
                                 :aliases #{sender-alias}}}))
              (catch Exception e (println (.printStackTrace e))))
            (alter contacts 
                   add-alias-to-contact
                   contact
                   sender-alias))))

(defn inbox-dispatch
  [socket]
  (let [stop-predicate (fn [line] (= line ":done"))
        message (make-command (read-until socket stop-predicate))
        contact (->> socket .getInetAddress .toString)
        message-data (json/read-json message)]
    (before (type message-data) update-contact (dbg contact) (dbg message-data))))

(defn create-inbox-server
  [port]
  (create-server port inbox-dispatch))
