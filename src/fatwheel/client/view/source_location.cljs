(ns fatwheel.client.view.source-location)

;; utils?
(defn action [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn SourceLocation [{:keys [src-loc selected?]} {:keys [select-src-loc]}]
  (let [{:keys [application? lang package method-name class-name file-name loaded-from line-number column]} src-loc]
    [:li {:className (when selected? "selected")
          :onClick (action #(select-src-loc src-loc))}
     [:span.strokeu
      [:span {:className (if application?
                           "icon application"
                           "icon")}]
      [:span.info
       (if (= lang :clj)
         [:span.name
          [:strong package]
          [:span.method "/" method-name]]
         [:span.name
          [:strong package "." class-name]
          [:span.method "$" method-name]])
       (if file-name
         [:div.location
          loaded-from " "
          [:span.filename file-name]
          ", line "
          line-number
          (when column
            (str ", column " column))]
         [:div.location "(unknown file)"])]]]))
