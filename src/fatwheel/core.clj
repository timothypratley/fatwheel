(ns fatwheel.core
  (:require [fatwheel.server :as server]
            [clojure.pprint :as pprint]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :as nsr]
            [clojure.tools.namespace.dir :as dir]
            [eastwood.lint :as e]
            [fatwheel.path-watcher :as pw]
            [integrant.core :as ig]
            [kibit.driver :as k]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]))

(def config
  {:adapter/server {:port 8080}
   :app/state {:server (ig/ref :adapter/server)}
   #_#_#_#_:file/watcher {}
   :toolchain [:lint
               :kibit]})


(defmethod ig/init-key :adapter/server [_ opts]
  (server/start opts))

(defmethod ig/halt-key! :adapter/server [_ this]
  (server/stop this))

(defmethod ig/init-key :toolchain/refresh [_])

(defmethod ig/init-key :app/state [_ {:keys [server]}]
  (doto (atom {})
    (add-watch
      :app-state-watch
      (fn app-state-changed [k r a b]
        (prn "SENDING" b)
        (server/ws-send server b)))))

(defmethod ig/halt-key! :app/state [_ this]
  (remove-watch this :app-state-watch))

(defonce program-namespaces (atom {}))

(add-watch
  program-namespaces
  :k
  (fn [k r a b]
    (println "PROGRAM:")
    (pprint/pprint
      (into {}
            (for [[k v] b]
              [k (map n/string v)])))))

(defn parse-file [f]
  (p/parse-file-all f))

;; TODO: doesn't quite work yet
(defn init [dir]
  (for [f (file-seq dir)]
    (swap! program-namespaces assoc (str f) (parse-file f))))

;; TODO: get this from project???
(defn src-dirs []
  ;; see https://dev.clojure.org/jira/browse/TNS-45
  ;;(mapv str (#'dir/dirs-on-classpath))
  ["src" "test"])

;; defines what functions get called
;; TODO: debounciness and concurrency
;; TODO: only run downstream tools on the parts that changed
(defn post-refresh-toolchain [app-state]
  (swap! app-state assoc :status :running-tests)
  (let [summary (test/run-all-tests)]
    ;;(swap! app-state assoc :test-summary summary)
    (if (test/successful? summary)
      (println "AWESOME!")
      (println "BOOO")))
  (swap! app-state assoc :status :linting)
  (let [lint (e/lint {})]
    (swap! app-state assoc :lint lint)
    (println lint))
  ;;(println (e/eastwood {}))
  ;; TODO: (dirs) does too much! (finds clojurescript intermediary files)
  (swap! app-state assoc :status :kibiting)
  (let [kibit (k/run ["src"] nil)]
    (swap! app-state assoc :kibit kibit)
    (println kibit))
  :ok)

(defn toolchain [app-state report-fn events-summary]
  (swap! app-state assoc :status :reloading)
  (apply nsr/set-refresh-dirs (src-dirs))
  (let [r (nsr/refresh)] ;; :after `post-refresh-toolchain)]
    (if (not= :ok r)
      (do
        (swap! app-state assoc :load-error (pr-str r))
        (println "OH NO:" r))
      (do
        (swap! app-state dissoc :load-error)
        ;; TODO: want to report during post-refresh-toolchain
        (post-refresh-toolchain app-state)))
    (swap! app-state assoc :status :waiting)
    (println "Toolchain complete, waiting for changes...")))

(defn -main [& args]
  (println "Fatwheel starting...")
  (nsr/disable-unload! *ns*)
  (let [{:keys [adapter/server app/state :as system]} (ig/init config)
        ;; TODO: better
        report-fn #(server/ws-send server %)]
    (pw/watch-dirs (src-dirs) init (fn [event-summary]
                                     (toolchain state report-fn event-summary))
                   10)
    (println "Exiting...")
    (ig/halt! system))
  (shutdown-agents))
