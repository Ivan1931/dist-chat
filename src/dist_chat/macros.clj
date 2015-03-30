(ns dist-chat.core)

(defmacro before
  [message & form]
  `(do (println (quote ~message) " ->\n" ~message "\n")
       ~form))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defmacro do-repeatedly
  [f & args]
  `(do ~@(map (fn [o] `(~f ~o)) args)))

(defmacro log-message
  [message body]
  `(let [x# ~body]
     (do (println "[LOG]" (.toString (Date. )) "|" ~message "-> " x#))
         x#))

(defmacro with-timeout [millis & body]
  `(let [f# (future ~@body)]
     (try 
       (.get f# ~millis java.util.concurrent.TimeUnit/MILLISECONDS)
       (catch java.util.concurrent.TimeoutException x#
         (do (future-cancel f#)
             nil)))))

(defmacro timed-future [timeout & body] 
  `(let [start# (System/currentTimeMillis)
         f# (future ~@body)]
     (loop []
       (let [elapsed-time# (- (System/currentTimeMillis) start#)]
         (cond (realized? f#) @f#
               (< ~timeout elapsed-time#) (do (future-cancel f#)
                                             :timeout)
               :else (recur))))))