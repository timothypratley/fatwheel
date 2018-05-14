(ns fatwheel.client.view.prone-model)

(defn get-active-src-locs [{:keys [error src-loc-selection debug-data]}]
  (case src-loc-selection
    :all (:frames error)
    :application (filter :application? (:frames error))
    :debug debug-data
    nil))

(defn select-current-error-in-chain [data]
  (if-let [other-error-path (get-in data [:paths :other-error])]
    (assoc data :error (get-in data other-error-path))
    (update-in data [:error] #(get-in % (-> data :paths :error)))))

(defn prepare-data-for-display [data]
  (-> (select-current-error-in-chain data)
      (assoc :active-src-locs (get-active-src-locs data))
      (assoc :selected-src-loc (get-in data [:selected-src-locs (:src-loc-selection data)]))))

(defn select-src-loc [data selection]
  (assoc-in data [:selected-src-locs (:src-loc-selection data)] selection))

(defn ensure-selected-src-loc [data]
  (let [view (select-current-error-in-chain data)
        active-src-locs (get-active-src-locs view)
        currently-selected (get-in view [:selected-src-locs (:src-loc-selection view)])]
    (if (some #{currently-selected} active-src-locs)
      data
      (select-src-loc data (first active-src-locs)))))

(defn navigate-data [data [path-key [nav-type path]]]
  (ensure-selected-src-loc
    (case nav-type
      :concat (update-in data [:paths path-key] #(concat % path))
      :reset (assoc-in data [:paths path-key] path))))


(defn change-src-loc-selection [data selection]
  (-> data
      (assoc :src-loc-selection selection)
      ensure-selected-src-loc))

(defn actions [prone-data]
  {:change-src-loc-selection #(swap! prone-data change-src-loc-selection %)
   :select-src-loc #(swap! prone-data select-src-loc %)
   :navigate-request nil
   :navigate-data #(swap! prone-data navigate-data %)
   :navigate-error nil})
