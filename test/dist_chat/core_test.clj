(ns dist-chat.core-test
  (:require [clojure.tools.trace :refer :all]
            [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [dist-chat.core :refer :all]))

(load "state_accessors_test")
(load "controller_test")
(load "reciever_test")
