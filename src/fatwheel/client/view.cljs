(ns fatwheel.client.view
  (:require [reagent.core :as reagent]
            [reagent.core :as reagent]
            [goog.dom :as dom]))

(defn container [app-state]
  [:div.container
   [:h1 "Hello world 2"]
   [:pre [:code (pr-str @app-state)]]])

(defn mount-root [app-state]
  (reagent/render [container app-state] (dom/getElement "app")))
