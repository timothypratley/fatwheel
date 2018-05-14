(ns fatwheel.client.view
  (:require [fatwheel.client.view.prone-ui :refer [ProneUI]]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [goog.dom :as dom]
            [cljs.pprint :as pprint]))

(defn container [app-state]
  (reagent/with-let
    [load-error (ratom/reaction (:load-error @app-state))]
    [:div.container
     [:pre [:code (pr-str (keys @app-state))]]
     [:h2 "Fatwheel is " (:status @app-state)]
     (if @load-error
       [ProneUI @load-error]
       [:h2 "Code loaded fine!"])
     (when-let [{:keys [warnings err err-data]} (:lint @app-state)]
       [:div
        [:h2 "Lint"]
        (when (seq warnings)
          [:div
           [:h3 "Warnings:"]
           (into [:ul]
                 (for [warning warnings]
                   [:li warning]))])
        (when err
          [:div
           [:h3 "Error: " (name err)]
           [:pre [:code (with-out-str (pprint/pprint err-data))]]])])
     (when-let [kibit (:kibit @app-state)]
       [:div
        [:h2 "Kibit"]
        [:pre [:code (with-out-str (pprint/pprint kibit))]]])]))


(defn mount-root [app-state]
  (reagent/render [container app-state] (dom/getElement "app")))
