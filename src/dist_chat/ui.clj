(ns dist-chat.core
  (:require [seesaw.core :refer :all]
            [seesaw.mig :refer [mig-panel]]
            [seesaw.dev :refer [show-options]]
            [seesaw.bind :as b]
            [seesaw.color :refer [color]]
            [seesaw.font :refer [font]]
            [cheshire.core :refer [generate-string]])
  (:import org.pushingpixels.substance.api.SubstanceLookAndFeel))

;; Contact online shown in title bar
;; Red button to delete a contact
;; Continuous save watch
;; Color send button green
;; If it's not too much trouble, get a bloody skin to work...


(defn- set-skin
  [skin-name]
  (SubstanceLookAndFeel/setSkin 
    (.getClassName (.get (SubstanceLookAndFeel/getAllSkins) skin-name))))

(def save-messages-action
  (menu-item :text "Save" 
             :listen [:action (fn [e] 
                                (save-contacts))]))

(def new-alias-action
  (menu-item :text "New Alias"
             :listen [:action (fn [e]
                                (when-let [new-alias (input "Enter your new alias")]
                                  (do 
                                    (reset! my-alias new-alias)
                                    (save-alias new-alias))))]))

(def continuous-save-toggle
  (menu-item :text "Toggle Continuous Save"
             :listen [:action (fn [e]
                                (do 
                                  (swap! continuous-save not)
                                  (save-continous-save)))]))

(def main-view
  (-> (frame :title (str "dist-chat - " @my-alias)
             :content "Hello there"
             :size [680 :by 400]
             :on-close :exit
             :menubar 
             (menubar :items 
                      [(menu :text "File" :items [save-messages-action new-alias-action])]))
      show!))


(defn message-to-str
  "Takes a message hash and converts it to a string"
  [{date :date
    message-text :message 
    alias-text :alias 
    is-reply :reply}]
  (let [sender-alias (if is-reply
                       @my-alias
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

(defn create-alias-string
  [ip alias-set]
  (if alias-set
    (string/join "/" alias-set)
    ip))

(defn render-contact
  [renderer data]
  (let [{:keys [value selected?]} data
        host-address (value 0)
        aliases (get-in value [1 :aliases])
        alias-string (create-alias-string (value 0)
                                          (aliases))]
    (config! renderer :text alias-string)))

(def contacts-list-box
  (listbox 
    :model @contacts
    :renderer (reify javax.swing.ListCellRenderer
                (getListCellRendererComponent
                  [this component value idx isSelected cellHasFocus]
                  (let [host-address (value 0)
                        aliases (get-in value [1 :aliases])
                        alias-string (if aliases 
                                       (string/join "/" aliases)
                                       (value 0))
                        background-color (if (even? idx)
                                           (seesaw.color/color 210 240 220)
                                           (seesaw.color/color 210 220 240))]
                    (label :text alias-string
                           :background (if isSelected
                                         (seesaw.color/color 230 210 210)
                                         background-color)))))))

(add-watch contacts :gui-update
           (fn [key ref old-state new-state]
             (let [last-selected (.getSelectedIndex contacts-list-box)]
               (do 
                 (config! contacts-list-box :model new-state)
                 (.setSelectedIndex contacts-list-box last-selected)
                 (try
                   (when @continuous-save
                     (try (save-contacts new-state)
                          (catch Exception e (log-exception "Problem writing to save-contacts" e)))))))))

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
    (listbox :model (get-in contact [1 :messages])
             :renderer (reify javax.swing.ListCellRenderer
                         (getListCellRendererComponent
                           [this component value idx isSelected cellHasFocus]
                           (let [date (value :date)
                                 is-reply (value :reply)
                                 sender-alias (if is-reply
                                                @my-alias
                                                (value :alias))
                                 background-color (if is-reply
                                                    (color 220 240 220)
                                                    (color 220 220 240))
                                 allignment (if is-reply
                                              :west
                                              :east)
                                 message-text (value :message)]
                             (border-panel allignment 
                                           (border-panel
                                             :north (label :text sender-alias
                                                           :font "ARIAL-BOLD-17")
                                             :center (label :text (.toString date)
                                                            :font (font :name :monospaced 
                                                                        :size 10 
                                                                        :styles #{:italic}))
                                             :south (text :text message-text
                                                          :background background-color
                                                          :multi-line? true
                                                          :editable? false)
                                             :background background-color
                                             :hgap 10
                                             :vgap 5))))))))

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
                 (text! message-field ""))
               (catch Exception e
                 (log-exception "Error sending message from GUI" e)))))))

(defn message-input
  [contact]
  (border-panel :center message-field
                :east (button :text "Send"
                              :action (action :handler message-send-handler)
                              :tip "Send message"
                              :background (color 220 240 220))
                :vgap 5 :hgap 5 :border 3))

(defn chat-interface
  [contact]
  (top-bottom-split (message-display-area contact)
                    (message-input contact)
                    :divider-location 8/9))


(def main-split
  (left-right-split contacts-display-area
                    (chat-interface (when (< 0 (count @contacts))
                                      (.elementAt (config contacts-list-box :model) 0)))))

(defn update-chat-interface
  [contact]
  (.setRightComponent main-split
                      (chat-interface contact)))

(.setSelectedIndex contacts-list-box 0)

(listen contacts-list-box :selection
        (fn [e]
          (update-chat-interface (value e))))

(display-main main-split)

(def online-check-wait 30000)

(defn start-online-check-loop
  []
  (future 
    (loop [current-user (selection contacts-list-box)]
      (let [ip (current-user 0)
            aliases (get-in current-user [1 :aliases])
            alias-string (create-alias-string ip aliases)
            is-online (is-online? ip inbox-port)
            title (str alias-string " - " (if is-online? "online" "offline"))]
        (do
          (config! main-view :title title)
          (Thread/sleep online-check-wait)
          (recur (selection contacts-list-box)))))))

(with-handler! (var start-online-check-loop)
  "Finds any exception that occurs during the check online phase"
  (fn [e] (log-exception "There was an error whilst performing check online loop" e)))

(start-online-check-loop)
