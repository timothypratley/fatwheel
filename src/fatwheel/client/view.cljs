(ns fatwheel.client.view
  (:require [fatwheel.client.view.prone-ui :refer [ProneUI]]
            [reagent.core :as reagent]
            [goog.dom :as dom]
            [cljs.pprint :as pprint]
            [clojure.string :as str]))

(def test-name
  {:fail "failure"
   :error "error"})

(defn render-test [{:keys [type file line] :as m}]
  [:div {:style {:color "red"}}
   [:pre [:code (with-out-str (pprint/pprint (dissoc m :type :file :line)))]]
   [:h2 "Test " (test-name type) " " file ":" line]])

(defn render-summary [summary]
  [:div {:style {:color "lightgreen"}}
   [:h2 "Tests passed"]])


(defn container [app-state]
  (let [{:keys [status load-error test lint kibit]} @app-state]
    [:div.container
     (if load-error
       ;; TODO: sometimes slow to mount, not sure why
       [ProneUI load-error]
       [:h2 "Code loaded"])
     (when-let [{:keys [error fail summary]} test]
       [:div
        (or (some-> error render-test)
            (some-> fail render-test)
            (some-> summary render-summary)
            [:h2 "Running tests"])])
     (when-let [{:keys [warnings err err-data]} lint]
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

     (when kibit
       [:div
        [:h2 "Kibit"]
        [:pre [:code (with-out-str (pprint/pprint kibit))]]])
     [:h2 "Fatwheel is " status]]))

(defn mount-root [app-state]
  (reagent/render [container app-state] (dom/getElement "app")))
