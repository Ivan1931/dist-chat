(ns dist-chat.core)

(defmacro before
  [message & form]
  `(do (println (quote ~message) " ->\n" ~message "\n")
       ~form))

(defmacro dbg [body]
  `(let [x# ~body]
     (println "dbg:" '~body "=" x#)
     x#))

(defn dbg-message
  "Prints a debug message to stdout"
  [message]
  (println "[DEBUG]" message))

(defmacro do-repeatedly
  [f & args]
  `(do ~@(map (fn [o] `(~f ~o)) args)))

(defmacro log-var
  "Logs the value of a variable. Prints the variable name and its value"
  [v]
  `(let [v# ~v]
     (do (println "[LOG]" (.toString (Date.)) "|" v "-> " v#))))

(defmacro log
  "Evaluates and returns a body. Logs result of the evaluation. Also prints a message with that"
  [output-type message body]
    `(let [x# ~body]
       (do (println "[" ~output-type "]" (.toString (Date. )) "|" ~message "-> " x#))
       x#))

(defmacro log-info
  [message body]
  `(log "INFO" ~message ~body))

(defmacro log-error
  [message body]
  `(log "ERROR" ~message ~body))

(defn- exception-to-str
  "e is an Exception
  returns the stack trace of e as a string. Stack trace is combined with lines"
  [e]
  (->> (.getStackTrace e)
       (map str)
       (string/join \newline)
       (str \newline)))

(defmacro log-exception
  [message e]
  `(log-error ~message
              (exception-to-str ~e)))

(defmacro timed-worker [timeout & body] 
  `(let [start# (System/currentTimeMillis)
         f# (future ~@body)]
     (loop []
       (let [elapsed-time# (- (System/currentTimeMillis) start#)]
         (cond (realized? f#) @f#
               (< ~timeout elapsed-time#) (do (future-cancel f#)
                                              :timeout)
               :else (recur))))))
