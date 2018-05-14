(ns fatwheel.client.view.map-browser
  (:require [clojure.walk :as walk]))


;; utils?
(defn action [f]
  (fn [e]
    (.preventDefault e)
    (f)))

(defn- get-in* [data path]
  "Like get-in, but looks up indexed values in lists too."
  (loop [data data
         path path]
    (if (seq path)
      (recur (if (and (list? data)
                      (number? (first path)))
               (nth data (first path))
               (get data (first path)))
             (rest path))
      data)))



(def inline-length-limit 120)

(defn serialized-value? [v]
  (:prone.prep/original-type v))

(defn- serialized-value-shorthand [v]
  (if (serialized-value? v)
    (:prone.prep/value v)
    v))

(defn- serialized-value-with-type [v]
  (if (serialized-value? v)
    (str (:prone.prep/value v) "<" (:prone.prep/original-type v) ">")
    v))

(defn- to-str [v]
  (pr-str (walk/prewalk serialized-value-shorthand v)))

(defn- too-long-for-inline? [v]
  (< inline-length-limit
     (.-length (pr-str (walk/prewalk serialized-value-with-type v)))))

(defn- get-token-class
  "These token classes are recognized by Prism.js, giving values in the map
  browser similar highlighting as the source code."
  [v]
  (str "token "
       (cond
         (string? v) "string"
         (number? v) "number"
         (keyword? v) "operator")))

(declare InlineToken)

(defn ValueToken
  "A simple value, render it with its type so it gets highlighted"
  [t]
  [:code {:className (get-token-class t)} (to-str t)])

(defn SerializedValueToken
  [t]
  [:span
   [InlineToken (:prone.prep/value t)]
   [:code.subtle "<" (:prone.prep/original-type t) ">"]])

(defn- format-inline-map [[k v] navigate-request]
  [[InlineToken k navigate-request]
   " "
   [InlineToken v navigate-request]])

(defn InlineMapBrowser
  "Display the map all in one line. The name implies browsability - this
   unfortunately is not in place yet. Work in progress."
  [m navigate-request]
  (let [kv-pairs (mapcat #(format-inline-map % navigate-request) m)]
    (into [:span]
          (concat ["{"] (interpose " " kv-pairs) ["}"]))))

(defn- format-list [l pre post]
  (into [:span]
        (flatten [pre (interpose " " (map InlineToken l)) post])))

(defn InlineVectorBrowser
  "Display a vector in one line"
  [v navigate-request]
  (format-list v "[" "]"))

(defn InlineListBrowser
  "Display a list in one line"
  [v navigate-request]
  (format-list v "(" ")"))

(defn InlineSetBrowser
  "Display a set in one line"
  [v navigate-request]
  (format-list v "#{" "}"))

(defn InlineToken
  "A value to be rendered roughly in one line. If the value is a list or a
   map, it will be browsable as well"
  [t navigate-request]
  (cond
    (serialized-value? t) (SerializedValueToken t)
    (map? t) (InlineMapBrowser t navigate-request)
    (vector? t) (InlineVectorBrowser t navigate-request)
    (list? t) (InlineListBrowser t navigate-request)
    (set? t) (InlineSetBrowser t navigate-request)
    :else (ValueToken t)))

(defn browseworthy-map?
  "Maps are only browseworthy if it is inconvenient to just look at the inline
  version (i.e., it is too big)"
  [m]
  (and (map? m)
       (not (serialized-value? m))
       (too-long-for-inline? m)))

(defn browseworthy-list?
  "Lists are only browseworthy if it is inconvenient to just look at the inline
  version (i.e., it is too big)"
  [t]
  (and (or (list? t) (vector? t))
       (too-long-for-inline? t)))

(defn MapSummary
  "A map summary is a list of its keys enclosed in brackets. The summary is
   given the comment token type to visually differentiate it from fully expanded
   maps"
  [k ks navigate-request]
  (let [too-long? (too-long-for-inline? ks)
        linked-keys (if too-long?
                      (str (count ks) " keys")
                      (interpose " " (map (fn [%] [:span (to-str %)]) ks)))]
    [:a {:href "#"
         :onClick (action #(navigate-request [:concat [k]]))}
     (into [:pre]
           (flatten ["{" linked-keys "}"]))]))

(defn ListSummary
  [k v navigate-request]
  [:a {:href "#"
       :onClick (action #(navigate-request [:concat [k]]))}
   (cond
     (list? v) [:pre "(" (count v) " items)"]
     (vector? v) [:pre "[" (count v) " items]"])])

(defn ProneMapEntry
  "A map entry is one key/value pair, formatted appropriately for their types"
  [[k v] navigate-request]
  [:tr
   [:td.name (InlineToken k navigate-request)]
   [:td (cond
          (browseworthy-map? v) (MapSummary k (keys v) navigate-request)
          (browseworthy-list? v) (ListSummary k v navigate-request)
          :else (InlineToken v navigate-request))]])

(defn MapPath
  "The heading and current path in the map. When browsing nested maps and lists,
   the path component will display the full path from the root of the map, with
   navigation options along the way."
  [path navigate-request]
  (let [paths (map #(take (inc %) path) (range (count path)))]
    (into [:span]
          (interpose " "
                     (conj
                       (mapv (fn [x]
                               [:a {:href "#"
                                    :onClick (action (fn [] (navigate-request [:reset x])))}
                                 (to-str (last x))])
                             (butlast paths))
                       (when-let [curr (last (last paths))]
                         (to-str curr)))))))

(defn MapBrowser [{:keys [name data path]} navigate-request]
  [:div
   [:h3.map-path
    (if (empty? path) name [:a {:href "#"
                                :onClick (action #(navigate-request [:reset []]))} name])
    " "
    (MapPath path navigate-request)]
   [:div.inset.variables
    [:table.var_table
     (into [:tbody]
           (let [view (get-in* data path)]
             (if (map? view)
               (map #(ProneMapEntry % navigate-request) (sort-by (comp str first) view))
               (map-indexed #(ProneMapEntry [%1 %2] navigate-request) view))))]]])
