(ns fatwheel.client.view.code-excerpt
  (:require [reagent.dom :as dom]
            [reagent.core :as reagent]
            [clojure.string :as str]))

(def modes
  {:clj "text/x-clojure"
   :java "text/x-java"})

(defn update-editor [editor lang code line column truncated-line-count]
  (doto editor
    (.setSize "100%" "100%")
    (.setOption "mode" (modes lang))
    (.setValue (str
                 (str/join (repeat \newline truncated-line-count))
                 code))
    (.addLineClass line "background" "line-error")
    (.focus)
    (.setCursor #js {:line line :ch column})))

(defn CodeBlock [lang code line column truncated-line-count]
  (let [editor (atom nil)]
    (reagent/create-class
      {:display-name "CodeBlock"

       :component-did-mount
       (fn [this]
         (reset!
           editor
           (doto
             (js/CodeMirror.fromTextArea
               (dom/dom-node this)
               #js {:lineNumbers true})
             (update-editor lang code line column truncated-line-count))))

       :component-will-unmount
       (fn [this]
         (.toTextArea @editor))

       :reagent-render
       (fn [lang code line column truncated-line-count]
         (some-> @editor (update-editor lang code line column truncated-line-count))
         ;; TODO: might want to allow editing in the future... fix it right there, send via websocket, save it back to disk
         [:textarea])})))

(defn CodeExcerpt [{:keys [method-name class-path-url line-number column lang source]}]
  [:div.trace_info {:style {:height "100%"}}
   (if-let [source-code (:code source)]
     [CodeBlock lang source-code (dec line-number) (or (dec column) 0) (:offset source)]
     [:section.source-failure (:failure source)])])
