(ns fatwheel.client.main
  (:require [fatwheel.client.routes :as routes]
            [fatwheel.client.communication :as communication]
            [fatwheel.client.notifier :as notifier]
            [fatwheel.client.view :as view]
            [reagent.core :as reagent]))

(defonce app-state (reagent/atom {}))

(defn init []
  (view/mount-root app-state)
  (routes/init)
  (communication/init app-state)
  (notifier/init app-state))

(init)
