(ns fatwheel.core
  (:require [clojure.pprint :as pprint]
            [clojure.test :as test]
            [fatwheel.path-watcher :as pw]
            [clojure.tools.namespace.repl :as nsr]
            [clojure.tools.namespace.dir :as dir]
            [eastwood.lint :as e]
            [kibit.driver :as k]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n])
  (:import (java.util.concurrent LinkedBlockingQueue)
           (java.util Vector)))

(defonce program-namespaces (atom {}))

;; defines what functions get called
;; TODO: debounciness and concurrency
(defn toolchain [events-summary]
  (let [r (nsr/refresh)]
    ;; TODO: only run downstream tools on the parts that changed
    (if (= :ok r)
      (if (test/run-all-tests)
        (do (println "AWESOME!")
            (println (e/eastwood {}))
            (println (k/run ["src"] nil)))
        (println "BOOO"))
      (do (println "OH NO")
          (println r)))))

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

(def debounce-t 10)

(defn debounce [^LinkedBlockingQueue event-queue t events]
  (Thread/sleep t)
  (let [recent-events (doto (Vector.) (->> (.drainTo event-queue)))
        accumulated-events (reduce conj events recent-events)
        [t] (last accumulated-events)
        elapsed (max 0 (- (System/currentTimeMillis) t))
        remaining-t (- debounce-t elapsed)]
    (if (<= remaining-t 0)
      accumulated-events
      (recur event-queue remaining-t accumulated-events))))

(def conjs (fnil conj #{}))

(defn summarize [events]
  (reduce (fn [acc [t kind path]]
            (update acc (str path) conjs kind))
          {}
          events))

(defn watch-dirs [dirs]
  (let [event-queue (LinkedBlockingQueue.)]
    (println "Watching the filesystem:" (pr-str dirs))
    (with-open [w (pw/make-watcher dirs (fn handle-event [kind path]
                                          (.put event-queue [(System/currentTimeMillis) kind path])))]
      (init dirs)
      (loop []
        (let [event (.take event-queue)
              events (debounce event-queue debounce-t [event])
              events-summary (summarize events)]
          (prn 'EVENTS events-summary)
          (try
            (toolchain events-summary)
            (catch Exception ex
              (println "Exception in toolchain")
              (println ex)))
          #_(let [n (find-ns 'fatwheel.core)]
              ((ns-resolve n 'toolchain) events-summary))
          (recur))))))

(defn -main [& args]
  (watch-dirs (map str (#'dir/dirs-on-classpath)))
  (println "Exiting...")
  (shutdown-agents))
