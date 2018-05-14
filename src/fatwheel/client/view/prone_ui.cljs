(ns fatwheel.client.view.prone-ui
  (:require [fatwheel.client.view.source-location :refer [SourceLocation]]
            [fatwheel.client.view.map-browser :refer [MapBrowser InlineVectorBrowser]]
            [fatwheel.client.view.code-excerpt :refer [CodeExcerpt]]
            [fatwheel.client.view.prone-model :as prone]
            [reagent.core :as reagent]
            [reagent.ratom :as ratom]
            [clojure.string :as str]))

;; utils?
(defn action [f]
  (fn [e]
    (.preventDefault e)
    (f)))


(defn Header [{:keys [error location paths]} {:keys [navigate-data]}]
  [:header.exception {:style {:grid-area "header"}}
   [:h2
    [:strong (:type error)]
    [:span " at "
     ;; TODO: location is always null so what is it meant to be?? omg?
     #_location
     (some->> error :message (re-find #"\(.*:\d+:\d+\)"))]

    (when (or (:caused-by error)
              (seq (:error paths))
              (:other-error paths))
      [:span.caused-by
       (if (seq (:other-error paths))
         [:a {:href "#"
              :onClick (action #(navigate-data [:other-error [:reset nil]]))}
          "< back"]
         (when (seq (:error paths))
           [:a {:href "#"
                :onClick (action #(navigate-data [:error [:reset (butlast (:error paths))]]))}
            "< back"]))
       (when-let [caused-by (:caused-by error)]
         [:span " Caused by " [:a {:href "#"
                                   :onClick (action #(navigate-data [:error [:concat [:caused-by]]]))}
                               (:type caused-by)]])])]
   [:p (or (some-> error
                   :message
                   (str/replace #"^java.lang.RuntimeException: " "")
                   (str/replace #", compiling:\(.*" ""))

           [:span (:class-name error)
            [:span.subtle " [no message]"]])]])

(defn SourceLocLink [src-loc-selection target name {:keys [change-src-loc-selection]}]
  [:a {:href "#"
       :className (when (= target src-loc-selection) "selected")
       :onClick (action #(change-src-loc-selection target))}
   name])

(defn Sidebar [{:keys [src-loc-selection selected-src-loc active-src-locs]} actions]
  [:nav.sidebar {:style {:grid-area "sidebar"}}
   [:nav.tabs
    [SourceLocLink src-loc-selection :application "Application Frames" actions]
    [SourceLocLink src-loc-selection :all "All Frames" actions]]
   (into [:ul#frames.frames]
         (for [src-loc active-src-locs]
           [SourceLocation
            {:src-loc src-loc, :selected? (= src-loc selected-src-loc)}
            actions]))])

(defn ExceptionsWhenRealizing [{:keys [total bounded? selection]} navigate-data]
  [:div.sub
   [:h3.map-path "Exceptions while realizing request map"]
   (when (< (count selection) total)
     [:div {:style {:fontSize 11 :marginBottom 15}} "Showing " (count selection) " out of " (when bounded? "at least ") total " exceptions."])
   [:div.inset.variables
    [:table.var_table
     (into [:tbody]
           (for [[path exception] selection]
             [:tr
              [:td.name [InlineVectorBrowser path nil]]
              [:td [:a {:href "#"
                        :onClick (action #(navigate-data [:other-error [:reset [:exceptions-when-realizing :selection path]]]))}
                    (or (:message exception)
                        (:class-name exception))]]]))]]])

(defn Body
  [{:keys [selected-src-loc error paths browsables]} {:keys [navigate-data]}]
  ;; TODO: I don't think there are any browsables???!!!??
  (let [local-browsables (:browsables error)]
    (into [:div#frame-info.frame_info {:style {:grid-area "body"}}
           [CodeExcerpt selected-src-loc]]
          (map (fn [x]
                 [:div.sub
                  [MapBrowser
                   {:data (:data x)
                    :path (get paths x)
                    :name (:name x)}
                   (fn [v] (navigate-data [x v]))]])
               (concat local-browsables browsables)))))

(defn ProneLayout [data actions]
  [:div.top
   {:style {:display "grid"
            :grid-template-columns "1fr 3fr"
            :grid-template-areas "'header header' 'sidebar body'"}}
   [Header data actions]
   [Sidebar data actions]
   [Body data actions]])

(defn ProneLayoutObserver [d actions]
  [ProneLayout @d actions])

(defn ProneUI [error-data]
  (reagent/with-let
    [prone-ui-state (reagent/atom nil)
     actions (prone/actions prone-ui-state)
     display-data (ratom/reaction (prone/prepare-data-for-display @prone-ui-state))]
    (reset! prone-ui-state (prone/ensure-selected-src-loc error-data))
    [ProneLayoutObserver display-data actions]))

