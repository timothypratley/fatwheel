(ns fatwheel.path-watcher
  "https://docs.oracle.com/javase/tutorial/essential/io/examples/WatchDir.java"
  (:import (java.nio.file FileSystems Paths StandardWatchEventKinds Path WatchService Files
                          SimpleFileVisitor FileVisitResult WatchKey WatchEvent LinkOption)
           (java.util HashMap Vector)
           (java.util.concurrent LinkedBlockingQueue)))

(def kinds
  {StandardWatchEventKinds/ENTRY_CREATE :create
   StandardWatchEventKinds/ENTRY_DELETE :delete
   StandardWatchEventKinds/ENTRY_MODIFY :modify
   StandardWatchEventKinds/OVERFLOW :overflow})

(defn get-path [^String s]
  (Paths/get s (make-array String 0)))
#_(get-path ".")

(defn- register ^WatchKey [^WatchService watch-service ^Path path ^HashMap m]
  (.put m (.register path watch-service (into-array (keys kinds))) path))
#_(with-open [watch-service (.newWatchService (FileSystems/getDefault))]
    (register watch-service (get-path ".")))

(defn- register-all [^WatchService watch-service ^Path path ^HashMap m]
  (Files/walkFileTree
    path
    (proxy [SimpleFileVisitor] []
      (preVisitDirectory [dir attrs]
        (register watch-service dir m)
        FileVisitResult/CONTINUE))))
#_(with-open [watch-service (.newWatchService (FileSystems/getDefault))]
    (register-all watch-service (get-path ".")))

(defn- maybe-register [^WatchService watch-service ^Path dir ^HashMap m kind ^Path child]
  (try
    (when (= kind :create)
      (if (Files/isDirectory child (into-array [LinkOption/NOFOLLOW_LINKS]))
        (register-all watch-service dir m)))
    (catch Exception ex
      (println "Exception while trying to register a new path" dir child)
      (println ex))))

(defn- take-events [^WatchService watch-service ^HashMap m f]
  (while (not (.isEmpty m))
    (let [^WatchKey watch-key (try (.take watch-service) (catch Exception ex))]
      (if watch-key
        (let [^Path dir (.get m watch-key)]
          (doseq [^WatchEvent ev (.pollEvents watch-key)
                  :let [kind (kinds (.kind ev))]
                  ;; TODO: not sure what to do on overflow
                  :when (not= kind :overflow)
                  :let [child (.resolve dir ^Path (.context ev))]]
            (maybe-register watch-service dir m kind child)
            (try
              ;; TODO: better filtering
              (when (re-matches #".+\.cljc?" (str child))
                (f kind child))
              (catch Exception ex
                (println "User function exception in watcher")
                (println ex))))
          (when-not (.reset watch-key)
            (.remove m watch-key)))
        (.clear m)))))

(defn- poll [^WatchService watch-service paths f]
  (let [m (HashMap.)]
    (doseq [path paths]
      (register-all watch-service path m))
    (future
      (try
        (take-events watch-service m f)
        (println "Path watcher stopped")
        (catch Exception ex
          (println "Path watcher threw an exception")
          (println ex))))))
#_(prn
    (with-open [watch-service (.newWatchService (FileSystems/getDefault))]
      (let [m (register watch-service (get-path "."))
            f (poll watch-service m (fn [x] (prn "hahaha")))]
        (spit "testfile" "hohohoho")
        f)))

;; TODO: filters
(defn make-watcher
  "Takes a pathname to watch, and a callback f.
  f should take 2 args: the event type and the event.
  Returns a closable watcher."
  ^java.nio.file.WatchService [dirs f]
  (doto (.newWatchService (FileSystems/getDefault))
    (poll (map get-path dirs) f)))
#_(with-open [w (make-watcher "." (fn event-handler [x y] (prn "HANDLE:" x y)))]
    (Thread/sleep 100)
    (spit "testfile2" "hahahaha")
    (spit "testfile" "hehehehe")
    (Thread/sleep 100))

(defn debounce [debounce-t ^LinkedBlockingQueue event-queue t events]
  (Thread/sleep t)
  (let [recent-events (doto (Vector.) (->> (.drainTo event-queue)))
        accumulated-events (reduce conj events recent-events)
        [t] (last accumulated-events)
        elapsed (max 0 (- (System/currentTimeMillis) t))
        remaining-t (- debounce-t elapsed)]
    (if (<= remaining-t 0)
      accumulated-events
      (recur debounce-t event-queue remaining-t accumulated-events))))

(def conjs (fnil conj #{}))

(defn summarize [events]
  (reduce (fn [acc [t kind path]]
            (update acc (str path) conjs kind))
          {}
          events))

(defn watch-dirs [dirs init f debounce-t]
  (let [event-queue (LinkedBlockingQueue.)]
    (println "Watching the filesystem:" (pr-str dirs))
    (with-open [w (make-watcher dirs (fn handle-event [kind path]
                                       (.put event-queue [(System/currentTimeMillis) kind path])))]
      (init dirs)
      (loop []
        (let [event (.take event-queue)
              events (debounce debounce-t event-queue debounce-t [event])
              events-summary (summarize events)]
          (prn 'EVENTS events-summary)
          (try
            (f events-summary)
            (catch Exception ex
              (println "Exception in toolchain")
              (println ex)))
          (recur))))))
