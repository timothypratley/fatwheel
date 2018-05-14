(ns fatwheel.client.main
  (:require [fatwheel.client.routes :as routes]
            [fatwheel.client.communication :as communication]
            [fatwheel.client.notifier :as notifier]
            [fatwheel.client.view :as view]
            [reagent.core :as reagent]))

(defn init [app-state]
  (routes/init)
  (communication/init app-state)
  (notifier/init app-state))

(defonce app-state
  (doto (reagent/atom {})
    (init)))

(defn mount-root []
  (view/mount-root app-state))

(mount-root)
