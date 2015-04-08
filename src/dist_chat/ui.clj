(ns dist-chat.core
  (:require [seesaw.core :refer :all]
            [seesaw.mig :refer [mig-panel]]
            [seesaw.dev :refer [show-options]]))


;; Figure out how to embed panels
;; Create some test data - done
;; Render test data on a panel
;; Display panel in split
;; Add text area for sending messages
;; Figure out how we are going to change between different contacts in gui
;; {:ip {:messages [messages] :online boolean :last-seen date :aliases [string] :meta {}}

(def test-data {"/127.0.0.1" {:messages [{:date (Date.) :message "Hello there man" :alias "John" :reply false} 
                                         {:date (Date.) :message "Hello there man" :alias "John" :reply false} 
                                         {:date (Date.) :message "Hey man, how are you????" :alias "Jonah" :reply true}]
                              :online false
                              :aliases #{"John"}}
                "/148.34.2.45" {:messages [{:date (Date.) :message "Hello there man" :alias "Nathan" :reply false} 
                                           {:date (Date.) :message "Hello there man" :alias "Josh" :reply false} 
                                           {:date (Date.) :message "Hey man, how are you????" :alias "Jonah" :reply true}]
                                :online true
                                :aliases #{"Josh" "Nathan"}}})
(defn message-to-str
  "Takes a message hash and converts it to a string"
  [{date :date
    message-text :message 
    alias-text :alias 
    is-reply :reply}]
  (let [sender-alias (if is-reply
                       "me"
                       alias-text)]
    (str sender-alias ": " 
         "Date: " (.toString date) \newline
         message-text)))

(defn messages-to-str
  "Converts an indexed collection of message hashes and converts them to strings"
  [messages]
  (->> messages
       (map message-to-str)
       (string/join \newline)))

(def main-view
  (-> (frame :title "dist-chat"
             :content "Hello there"
             :size [680 :by 400]
             :on-close :exit)
      pack!
      show!))

(defn display
  "Sets the content of a larger view"
  [component content]
  (config! component
           :content content)
  content)

(defn display-main
  "Displays a component on the main view"
  [content]
  (display main-view content))

(defn render-contact
  [renderer data]
  (let [{:keys [value selected?]} data
        aliases (get-in value [1 :aliases])
        alias-string (if aliases 
                       (string/join "/" aliases)
                       (value 0))]
    (config! renderer :text alias-string)))

(def contacts-list-box
  (listbox :model (vec test-data)
           :renderer render-contact))

(defn add-contact-handler
  "Adds a new contact to our contacts hash"
  [e]
  (let [new-contact (input "Enter contact IP address")
        new-contact-alias (input "Enter an alias for contact")]
    (if (and new-contact new-contact-alias)
      (dosync (alter contacts add-alias-to-contact 
                              new-contact 
                              new-contact-alias))
        nil)))

(def add-contact-button
  (button :text "Add Contact"
          :listen [:action add-contact-handler]))


(def contacts-display-area
  (border-panel :north contacts-list-box
                :south add-contact-button))

(defn message-display-area
  [contact]
  (scrollable
    (text :multi-line? true
          :editable? false
          :wrap-lines? true
          :text (-> contact
                    (get-in [1 :messages])
                    messages-to-str))))

(def message-field
  (text :text ""
        :multi-line? true
        :wrap-lines? true))

(defn message-send-handler
  [e]
  (let [recipient-data (selection contacts-list-box)
        host (recipient-data 0)
        message-content (config message-field :text)
        message-hash {:ip host
                      :message message-content}]
    (future 
       (do (log-info "Sending message from GUI" message-hash)
           (try (let [send-result (send-message message-hash)]
                  (println "Finished" send-result))
                    (catch Exception e
                    (log-exception "Error sending message from GUI" e)))))))

(defn message-input
  [contact]
  (border-panel :center message-field
                :east (button :text "Send"
                              :action (action :handler message-send-handler))
                :vgap 5 :hgap 5 :border 3))

(defn chat-interface
  [contact]
  (top-bottom-split (message-display-area contact)
                    (message-input contact)
                    :divider-location 8/9))

(def main-split
    (left-right-split contacts-display-area
                      (chat-interface (selection contacts-list-box))))

(defn update-chat-interface
  [contact]
  (.setRightComponent main-split
                      (chat-interface contact)))
(listen contacts-list-box :selection
        (fn [e]
          (update-chat-interface (value e))))

(display-main main-split)
;; We need to update the state of the ui when ever we recieve a new message
(add-watch contacts :update-gui
           (fn [-ref -key old new]
             (let [current-selection (selection contacts-list-box)]
                  (do (config! contacts-list-box 
                               :model (vec new))
                      (selection! contacts-list-box current-selection)))))
