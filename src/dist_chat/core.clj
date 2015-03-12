(ns dist-chat.core
  (:import (java.net Socket)
           (java.io PrintWriter InputStreamReader BufferedReader))
  (:require [clj-sockets.core :as sock])
  (:require [clojure.tools.namespace.repl :refer [refresh]]))

(load "server")
(load "client")
