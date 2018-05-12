(ns fatwheel.client.notifier
  (:require [clojure.data :as data]
            [clojure.string :as str]))

(defn make-notification [title body]
  (js/Notification.
    title
    #js {:type "basic"
         :tag "fatwheel"
         :icon "favicon.png"
         :body body}))

(defn notify [title body]
  (cond
    (not js/Notification)
    (js/alert "This browser does not support system notifications")

    (= js/Notification.permission "granted")
    (make-notification title body)

    (not= js/Notification.permission "denied")
    (js/Notification.requestPermission
      (fn [perm]
        (when (= "granted" perm)
          (make-notification title body))))))

(defn summarize [a b]
  (let [[only-in-a only-in-b in-both] (data/diff a b)
        title (str
                (when (seq only-in-a)
                  (str "Fixed: " (str/join ", " (keys only-in-a))))
                (when (seq only-in-b)
                  (str "Broke: " (str/join "," (keys only-in-b)))))
        body (when (seq only-in-b)
               (first only-in-b))]
    (notify title body)))

(defn init [app-state]
  (notify "Fatwheel started" "Watching for changes...")
  (add-watch app-state :notifier-watch-app-state
             (fn [k r a b]
               (prn "SUP" a b)
               (summarize (:server-state a) (:server-state b)))))
