(ns fatwheel.core
  (:require [fatwheel.server :as server]
            [clojure.pprint :as pprint]
            [clojure.test :as test]
            [clojure.tools.namespace.repl :as nsr]
            [clojure.tools.namespace.dir :as dir]
            [eastwood.lint :as e]
            [eftest.runner :as runner]
            [eftest.report.pretty :as pretty]
            [eftest.report.progress :as progress]
            [fatwheel.path-watcher :as pw]
            [integrant.core :as ig]
            [kibit.driver :as k]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [prone.stacks :as stacks]
            [prone.middleware :as pm]
            [prone.prep :as prep]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def config
  {:app/state {}
   :websocket/message-handler {:app-state (ig/ref :app/state)}
   ;; TODO: separate http-kit server from websocket server (handler&sente)
   :http/server {:opts {:port 8080}
                 :message-handler (ig/ref :websocket/message-handler)}
   :websocket/notifier {:server (ig/ref :http/server)
                        :app-state (ig/ref :app/state)}
   ;; TODO: in theory Integrant allows users to configure their toolchain
   #_#_#_#_:file/watcher {}
       :toolchain [:lint
                   :kibit]})

(defmethod ig/init-key :app/state [_ _]
  (atom {}))

(defmethod ig/init-key :websocket/message-handler [_ {:keys [app-state]}]
  (fn event-msg-handler [{:keys [id event ?data ring-req ?reply-fn send-fn]}]
    (let [uid (:uid (:session ring-req))]
      (condp = id
        :fatwheel/hello
        (do
          (println "Sending initial state:" (keys @app-state))
          ;; TODO: omg why??? WHY??? Oh the inhumanity
          (send-fn (or uid :sente/all-users-without-uid)
                   [:fatwheel/app-state @app-state]))

        ;; This doesn't raise when reconnecting, which is kinda annoying
        :chsk/uidport-open
        (println "New connection:" uid)

        :chsk/uidport-close
        (println "Disconnected:" uid)

        :chsk/ws-ping
        nil

        (do
          (println "Unhandled event:" event)
          (when ?reply-fn
            (?reply-fn {:umatched-event-as-echoed-from-from-server event})))))))


(defmethod ig/init-key :http/server [_ {:keys [opts message-handler]}]
  (server/start message-handler opts))

(defmethod ig/halt-key! :http/server [_ this]
  ;; TODO: is this really this???
  (server/stop this))

(defmethod ig/init-key :websocket/notifier [_ {:keys [server app-state]}]
  (add-watch app-state
    :app-state-watch
    (fn app-state-changed [k r a b]
      (println "Sending update:" (:keys b))
      (server/ws-send server [:fatwheel/app-state b]))))

(defmethod ig/halt-key! :websocket/notifier [_ this]
  ;; TODO: is this really this???
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
  ;; TODO: track tests more precisely
  (let [summary (runner/run-tests (runner/find-tests "test")
                                  {:fail-fast? true
                                   :report (fn test-report [{:keys [type] :as m}]
                                             (when (#{:fail :error} type)
                                               (swap! app-state assoc-in [:test type] m))
                                             (progress/report m))})
        #_(test/run-all-tests)]
    (swap! app-state assoc-in [:test :summary] summary)
    (if (not (test/successful? summary))
      (println "BOOO")
      (do
        (println "AWESOME!")
        (swap! app-state (fn [x]
                           (-> x
                               (update :test dissoc :fail :error)
                               (assoc :status :linting))))
        (let [lint (e/lint {})]
          (swap! app-state assoc :lint lint)
          (println lint))
        ;;(println (e/eastwood {}))
        ;; TODO: (dirs) does too much! (finds clojurescript intermediary files)
        ;; TODO: should kibit be chained?
        (swap! app-state assoc :status :kibiting)
        (let [kibit (k/run ["src"] nil)]
          (swap! app-state assoc :kibit kibit)
          (println kibit)))))
  :ok)

(defn parse-int [s]
  (try
    (Integer/parseInt s)
    (catch Exception ex
      0)))

(defn toolchain [app-state report-fn events-summary]
  (swap! app-state assoc :status :reloading)
  (apply nsr/set-refresh-dirs (src-dirs))
  (let [r (nsr/refresh)] ;; :after `post-refresh-toolchain)]
    (if (not= :ok r)
      (do
        ;; TODO: scope capture
        (swap! app-state assoc :load-error
               ;; TODO: get-application-name relies on lein, instead just look in the src dir?
               (let [e
                     (with-redefs [stacks/add-frame-from-message
                                   (fn [ex]
                                     ;; TODO: merge upstream to prone
                                     (if-let [data (and (:message ex)
                                                        (re-find #"\(([^(]+.cljc?):(\d+):(\d+)\)" (:message ex)))]
                                       (let [[_ path line column] data]
                                         (if (io/resource path)
                                           (update-in ex [:frames]
                                                      #(conj % {:lang :clj
                                                                :package (#'stacks/file-name-to-namespace path)
                                                                :method-name nil
                                                                :loaded-from nil
                                                                :class-path-url path
                                                                :file-name (re-find #"[^/]+.cljc?" path)
                                                                :line-number (Integer. line)
                                                                :column (Integer. column)}))
                                           ex))
                                       ex))]
                       ;; TODO: shorten exception type
                       (#'prep/prep-error (stacks/normalize-exception r) [(#'pm/get-application-name)]))]
                 {:title (:message e)
                  :error e
                  :src-loc-selection :application}))
        (println "OH NO:" r))
      (do
        ;; TODO: want to report during post-refresh-toolchain, but vars are gone
        (swap! app-state dissoc :load-error)
        (post-refresh-toolchain app-state)))
    (swap! app-state assoc :status :waiting)
    (println "Toolchain complete, waiting for changes...")))

(defn -main [& args]
  (println "Fatwheel starting...")
  (nsr/disable-unload! *ns*)
  (let [{:keys [http/server app/state :as system]} (ig/init config)
        ;; TODO: better
        report-fn (fn report-fn [x]
                    (server/ws-send server [:fatwheel/app-state x]))
        on-change (fn on-file-change [event-summary]
                    (toolchain state report-fn event-summary))]
    (pw/watch-dirs (src-dirs) on-change on-change 10)
    (println "Exiting...")
    (ig/halt! system))
  (shutdown-agents))
