(ns fatwheel.client.routes
  (:require [goog.events :as events]
            [goog.history.EventType :as EventType])
  (:import (goog History)))


(defn navigate [])

(defn init [])

(defonce history
  (doto (History.)
    (events/removeAll)
    (events/listen EventType/NAVIGATE (fn on-navigate [e]
                                        (#'navigate e)))
    (.setEnabled true)))
